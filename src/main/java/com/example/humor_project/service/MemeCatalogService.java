package com.example.humor_project.service;

import com.example.humor_project.model.BatchStatus;
import com.example.humor_project.model.MemeArchiveMonth;
import com.example.humor_project.model.MemeCategory;
import com.example.humor_project.model.MemeItem;
import com.example.humor_project.model.SourceType;
import com.example.humor_project.persistence.entity.MemeBatchEntity;
import com.example.humor_project.persistence.entity.MemeCategoryEntity;
import com.example.humor_project.persistence.entity.MemeSnapshotEntity;
import com.example.humor_project.persistence.entity.MemeSourceConfigEntity;
import com.example.humor_project.persistence.repository.MemeBatchRepository;
import com.example.humor_project.persistence.repository.MemeCategoryRepository;
import com.example.humor_project.persistence.repository.MemeSnapshotRepository;
import com.example.humor_project.persistence.repository.MemeSourceConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MemeCatalogService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_ITEMS_PER_CATEGORY = 10;
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

	private final MemeCategoryRepository categoryRepository;
	private final MemeSourceConfigRepository sourceConfigRepository;
	private final MemeBatchRepository batchRepository;
	private final MemeSnapshotRepository snapshotRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	@Value("${meme.catalog.warm-up-enabled:true}")
	private boolean warmUpEnabled;

	private volatile List<MemeCategory> cachedCategories = fallbackCatalog();
	private volatile List<MemeArchiveMonth> cachedArchiveMonths = List.of();
	private volatile LocalDate lastUpdatedDate = LocalDate.now(KST);

	public MemeCatalogService(
			MemeCategoryRepository categoryRepository,
			MemeSourceConfigRepository sourceConfigRepository,
			MemeBatchRepository batchRepository,
			MemeSnapshotRepository snapshotRepository
	) {
		this.categoryRepository = categoryRepository;
		this.sourceConfigRepository = sourceConfigRepository;
		this.batchRepository = batchRepository;
		this.snapshotRepository = snapshotRepository;
	}

	@PostConstruct
	public void warmUp() {
		ensureDefaultCatalogConfig();
		loadPersistedCatalog();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void triggerStartupRefresh() {
		if (!warmUpEnabled) {
			return;
		}
		CompletableFuture.runAsync(() -> refreshCatalogSafely(LocalDate.now(KST)));
	}

	public List<MemeCategory> getTrendingCategories() {
		return cachedCategories;
	}

	public LocalDate getLastUpdatedDate() {
		return lastUpdatedDate;
	}

	public List<MemeArchiveMonth> getArchiveMonths() {
		return cachedArchiveMonths;
	}

	public LocalDate getNextUpdateDate() {
		return lastUpdatedDate.plusWeeks(1);
	}

	@Scheduled(cron = "${meme.refresh.cron:0 0 0 * * MON}", zone = "Asia/Seoul")
	public void refreshWeekly() {
		refreshCatalogSafely(LocalDate.now(KST));
	}

	private synchronized void refreshCatalogSafely(LocalDate refreshDate) {
		ensureDefaultCatalogConfig();
		Instant startedAt = Instant.now();
		MemeBatchEntity batch = batchRepository.save(new MemeBatchEntity(refreshDate, startedAt, BatchStatus.RUNNING));
		try {
			List<MemeCategoryEntity> categories = categoryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
			List<MemeSourceConfigEntity> configs = sourceConfigRepository.findAllByActiveTrueOrderByCategory_DisplayOrderAscDisplayOrderAscIdAsc();
			List<MemeCategory> liveCatalog = buildLiveCatalog(categories, configs);
			List<MemeCategory> mergedCatalog = mergeWithRecentHistory(categories, liveCatalog);
			int itemCount = mergedCatalog.stream().mapToInt(category -> category.items().size()).sum();
			if (itemCount > 0) {
				persistSuccessfulBatch(batch, categories, mergedCatalog, Instant.now());
				cachedCategories = mergedCatalog;
				lastUpdatedDate = refreshDate;
				loadArchiveMonths(categories);
				return;
			}
			saveFailedBatch(batch, "No image memes fetched", Instant.now());
		} catch (Exception exception) {
			saveFailedBatch(batch, truncate(exception.getMessage(), 500), Instant.now());
		}
		loadPersistedCatalog();
	}

	private void loadPersistedCatalog() {
		List<MemeCategoryEntity> categories = categoryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
		Optional<MemeBatchEntity> latestBatch = batchRepository.findTopByStatusOrderByRunDateDescStartedAtDesc(BatchStatus.SUCCESS);
		if (latestBatch.isPresent()) {
			cachedCategories = mapPersistedCatalog(categories, loadSnapshots(latestBatch.get().getId()));
			lastUpdatedDate = latestBatch.get().getRunDate();
			loadArchiveMonths(categories);
			return;
		}
		if (!categories.isEmpty()) {
			cachedCategories = categories.stream()
					.map(category -> new MemeCategory(
							category.getCategoryKey(),
							category.getName(),
							category.getDescription(),
							List.of()
					))
					.collect(Collectors.toList());
		}
		cachedArchiveMonths = List.of();
	}

	private List<MemeCategory> buildLiveCatalog(List<MemeCategoryEntity> categories, List<MemeSourceConfigEntity> configs) {
		if (categories.isEmpty()) {
			return List.of();
		}

		Map<Long, List<MemeItem>> bucket = new LinkedHashMap<>();
		for (MemeCategoryEntity category : categories) {
			bucket.put(category.getId(), new ArrayList<>());
		}

		Map<Long, List<MemeSourceConfigEntity>> configsByCategory = configs.stream()
				.filter(config -> config.getSourceType() == SourceType.REDDIT)
				.collect(Collectors.groupingBy(config -> config.getCategory().getId(), LinkedHashMap::new, Collectors.toList()));

		for (MemeCategoryEntity category : categories) {
			List<MemeSourceConfigEntity> effectiveConfigs = configsByCategory.getOrDefault(category.getId(), List.of());
			if (effectiveConfigs.isEmpty()) {
				effectiveConfigs = buildDefaultPreferredConfigs(category);
			}
			for (MemeSourceConfigEntity config : effectiveConfigs) {
				bucket.get(category.getId()).addAll(fetchItems(config));
			}
		}

		List<MemeCategory> result = new ArrayList<>();
		for (MemeCategoryEntity category : categories) {
			List<MemeItem> selected = bucket.get(category.getId()).stream()
					.filter(this::isImageOnly)
					.collect(Collectors.toMap(
							item -> item.sourceUrl() + "|" + item.title(),
							item -> item,
							(left, right) -> left,
							LinkedHashMap::new
					))
					.values()
					.stream()
					.sorted(Comparator.comparingLong(MemeItem::popularity).reversed())
					.limit(MAX_ITEMS_PER_CATEGORY)
					.collect(Collectors.toList());

			result.add(new MemeCategory(
					category.getCategoryKey(),
					category.getName(),
					category.getDescription(),
					selected
			));
		}
		return result;
	}

	private List<MemeItem> fetchItems(MemeSourceConfigEntity config) {
		if (config.getSourceType() != SourceType.REDDIT) {
			return List.of();
		}
		return fetchRedditTopImages(config.getQueryValue(), config.getFetchLimit());
	}

	private List<MemeSourceConfigEntity> buildDefaultPreferredConfigs(MemeCategoryEntity category) {
		return defaultSourceSeeds().stream()
				.filter(seed -> seed.categoryKey().equals(category.getCategoryKey()))
				.map(seed -> new InMemorySourceConfig(seed.sourceType(), seed.queryValue(), seed.fetchLimit(), seed.regionCode()))
				.collect(Collectors.toList());
	}

	private List<MemeCategory> mergeWithRecentHistory(List<MemeCategoryEntity> categories, List<MemeCategory> liveCatalog) {
		List<MemeBatchEntity> successfulBatches = batchRepository.findAllByStatusOrderByRunDateDescStartedAtDesc(BatchStatus.SUCCESS);
		if (successfulBatches.isEmpty()) {
			return liveCatalog;
		}

		Map<String, List<MemeItem>> historicalItemsByCategory = buildHistoricalItemsByCategory(categories, successfulBatches);
		Map<String, MemeCategory> liveByKey = liveCatalog.stream()
				.collect(Collectors.toMap(MemeCategory::key, category -> category, (left, right) -> left, LinkedHashMap::new));

		return categories.stream()
				.map(categoryEntity -> {
					MemeCategory live = liveByKey.get(categoryEntity.getCategoryKey());
					List<MemeItem> mergedItems = topOffItems(
							live == null ? List.of() : live.items(),
							historicalItemsByCategory.getOrDefault(categoryEntity.getCategoryKey(), List.of())
					);
					return new MemeCategory(
							categoryEntity.getCategoryKey(),
							categoryEntity.getName(),
							categoryEntity.getDescription(),
							mergedItems
					);
				})
				.collect(Collectors.toList());
	}

	@Transactional
	protected void persistSuccessfulBatch(
			MemeBatchEntity batch,
			List<MemeCategoryEntity> categories,
			List<MemeCategory> catalog,
			Instant endedAt
	) {
		Map<String, MemeCategoryEntity> categoryByKey = categories.stream()
				.collect(Collectors.toMap(MemeCategoryEntity::getCategoryKey, category -> category));
		List<MemeSnapshotEntity> snapshots = new ArrayList<>();
		int itemCount = 0;
		for (MemeCategory category : catalog) {
			MemeCategoryEntity categoryEntity = categoryByKey.get(category.key());
			if (categoryEntity == null) {
				continue;
			}
			for (int index = 0; index < category.items().size(); index++) {
				MemeItem item = category.items().get(index);
				snapshots.add(new MemeSnapshotEntity(
						batch,
						categoryEntity,
						truncate(item.title(), 255),
						truncate(item.type(), 32),
						truncate(item.mediaUrl(), 1000),
						truncate(item.sourceUrl(), 1000),
						truncate(item.summary(), 500),
						truncate(item.tags(), 255),
						truncate(item.source(), 64),
						item.popularity(),
						index + 1
				));
				itemCount++;
			}
		}
		batch.markFinished(BatchStatus.SUCCESS, "Fetched " + itemCount + " meme items", itemCount, endedAt);
		batchRepository.save(batch);
		snapshotRepository.saveAll(snapshots);
	}

	@Transactional
	protected void saveFailedBatch(MemeBatchEntity batch, String message, Instant endedAt) {
		batch.markFinished(BatchStatus.FAILED, truncate(message, 500), 0, endedAt);
		batchRepository.save(batch);
	}

	private List<MemeCategory> mapPersistedCatalog(List<MemeCategoryEntity> categories, List<MemeSnapshotEntity> snapshots) {
		Map<String, List<MemeItem>> itemsByCategory = new LinkedHashMap<>();
		for (MemeCategoryEntity category : categories) {
			itemsByCategory.put(category.getCategoryKey(), new ArrayList<>());
		}
		for (MemeSnapshotEntity snapshot : snapshots) {
			if (!isCuratedSnapshot(snapshot)) {
				continue;
			}
			String categoryKey = snapshot.getCategory().getCategoryKey();
			List<MemeItem> items = itemsByCategory.get(categoryKey);
			if (items == null) {
				continue;
			}
			items.add(new MemeItem(
					snapshot.getTitle(),
					snapshot.getMediaType(),
					snapshot.getMediaUrl(),
					false,
					snapshot.getSourceUrl(),
					snapshot.getSummary(),
					snapshot.getTags(),
					snapshot.getSource(),
					snapshot.getPopularity()
			));
		}
		return categories.stream()
				.map(category -> new MemeCategory(
						category.getCategoryKey(),
						category.getName(),
						category.getDescription(),
						itemsByCategory.getOrDefault(category.getCategoryKey(), List.of())
				))
				.collect(Collectors.toList());
	}

	private void loadArchiveMonths(List<MemeCategoryEntity> categories) {
		List<MemeBatchEntity> successfulBatches = batchRepository.findAllByStatusOrderByRunDateDescStartedAtDesc(BatchStatus.SUCCESS);
		if (successfulBatches.isEmpty()) {
			cachedArchiveMonths = List.of();
			return;
		}

		LocalDate currentDate = successfulBatches.get(0).getRunDate();
		Map<LocalDate, MemeBatchEntity> latestBatchByWeek = new LinkedHashMap<>();
		for (MemeBatchEntity batch : successfulBatches) {
			LocalDate weekStart = startOfWeek(batch.getRunDate());
			latestBatchByWeek.putIfAbsent(weekStart, batch);
		}

		List<MemeArchiveMonth> archives = new ArrayList<>();
		boolean skipCurrentWeek = true;
		for (Map.Entry<LocalDate, MemeBatchEntity> entry : latestBatchByWeek.entrySet()) {
			if (skipCurrentWeek) {
				skipCurrentWeek = false;
				continue;
			}
			MemeBatchEntity batch = entry.getValue();
			List<MemeSnapshotEntity> curatedSnapshots = loadSnapshots(batch.getId()).stream()
					.filter(this::isCuratedSnapshot)
					.collect(Collectors.toList());
			if (curatedSnapshots.isEmpty()) {
				continue;
			}
			archives.add(new MemeArchiveMonth(
					weeklyArchiveLabel(currentDate, batch.getRunDate()),
					batch.getRunDate(),
					mapPersistedCatalog(categories, curatedSnapshots)
			));
		}
		cachedArchiveMonths = archives;
	}

	private List<MemeSnapshotEntity> loadSnapshots(Long batchId) {
		return snapshotRepository.findAllByBatch_IdOrderByCategory_DisplayOrderAscRankOrderAscIdAsc(batchId);
	}

	private Map<String, List<MemeItem>> buildHistoricalItemsByCategory(List<MemeCategoryEntity> categories, List<MemeBatchEntity> successfulBatches) {
		Map<String, List<MemeItem>> itemsByCategory = new LinkedHashMap<>();
		Map<String, Map<String, MemeItem>> uniqueByCategory = new LinkedHashMap<>();
		for (MemeCategoryEntity category : categories) {
			itemsByCategory.put(category.getCategoryKey(), new ArrayList<>());
			uniqueByCategory.put(category.getCategoryKey(), new LinkedHashMap<>());
		}

		for (MemeBatchEntity batch : successfulBatches) {
			for (MemeSnapshotEntity snapshot : loadSnapshots(batch.getId())) {
				if (!isCuratedSnapshot(snapshot)) {
					continue;
				}
				String categoryKey = snapshot.getCategory().getCategoryKey();
				Map<String, MemeItem> uniqueItems = uniqueByCategory.get(categoryKey);
				if (uniqueItems == null) {
					continue;
				}
				String identity = snapshot.getSourceUrl() + "|" + snapshot.getTitle();
				uniqueItems.putIfAbsent(identity, new MemeItem(
						snapshot.getTitle(),
						snapshot.getMediaType(),
						snapshot.getMediaUrl(),
						false,
						snapshot.getSourceUrl(),
						snapshot.getSummary(),
						snapshot.getTags(),
						snapshot.getSource(),
						snapshot.getPopularity()
				));
			}
		}

		for (Map.Entry<String, Map<String, MemeItem>> entry : uniqueByCategory.entrySet()) {
			itemsByCategory.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
		}
		return itemsByCategory;
	}

	private List<MemeItem> topOffItems(List<MemeItem> preferredItems, List<MemeItem> historicalItems) {
		Map<String, MemeItem> merged = new LinkedHashMap<>();
		for (MemeItem item : preferredItems) {
			merged.putIfAbsent(item.sourceUrl() + "|" + item.title(), item);
			if (merged.size() >= MAX_ITEMS_PER_CATEGORY) {
				return new ArrayList<>(merged.values());
			}
		}
		for (MemeItem item : historicalItems) {
			merged.putIfAbsent(item.sourceUrl() + "|" + item.title(), item);
			if (merged.size() >= MAX_ITEMS_PER_CATEGORY) {
				break;
			}
		}
		return new ArrayList<>(merged.values());
	}

	private LocalDate startOfWeek(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
	}

	private String weeklyArchiveLabel(LocalDate currentDate, LocalDate snapshotDate) {
		long weeksBetween = ChronoUnit.WEEKS.between(startOfWeek(snapshotDate), startOfWeek(currentDate));
		if (weeksBetween <= 1) {
			return "Last Week";
		}
		if (weeksBetween == 2) {
			return "2 Weeks Ago";
		}
		return weeksBetween + " Weeks Ago";
	}

	private List<MemeItem> fetchRedditTopImages(String subreddit, int limit) {
		List<MemeItem> items = new ArrayList<>();
		String url = "https://www.reddit.com/r/" + subreddit + "/top.json?t=week&raw_json=1&limit=" + Math.max(limit, 1);
		try {
			JsonNode root = getJson(url, Map.of("User-Agent", "meme-pulse/1.0"));
			for (JsonNode child : root.path("data").path("children")) {
				JsonNode data = child.path("data");
				if (data.path("over_18").asBoolean(false)) {
					continue;
				}
				String mediaUrl = resolveRedditMediaUrl(data);
				if (mediaUrl.isBlank()) {
					continue;
				}
				if (!"image".equals(inferMediaType(mediaUrl))) {
					continue;
				}

				long score = data.path("score").asLong(0);
				long comments = data.path("num_comments").asLong(0);
				String title = data.path("title").asText("Untitled");
				String permalink = data.path("permalink").asText("");
				String sourceUrl = permalink.isBlank()
						? "https://www.reddit.com/r/" + subreddit
						: "https://www.reddit.com" + permalink;

				items.add(new MemeItem(
						title,
						"image",
						mediaUrl,
						false,
						sourceUrl,
						"Weekly top image from r/" + subreddit + " / score " + score + " / comments " + comments
								+ " / fetched " + DATE_FORMATTER.format(LocalDate.now(KST)),
						subreddit + ", reddit",
						"Reddit",
						score
				));
			}
		} catch (Exception ignored) {
			return List.of();
		}
		return items;
	}

	private JsonNode getJson(String url, Map<String, String> headers) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/json");
		for (Map.Entry<String, String> header : headers.entrySet()) {
			builder.header(header.getKey(), header.getValue());
		}
		HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return objectMapper.readTree(response.body());
	}

	private String resolveRedditMediaUrl(JsonNode data) {
		String direct = data.path("url_overridden_by_dest").asText(data.path("url").asText(""));
		if (isLikelyMediaUrl(direct)) {
			return sanitizeMediaUrl(direct);
		}
		String preview = data.path("preview").path("images").path(0).path("source").path("url").asText("");
		if (isLikelyMediaUrl(preview)) {
			return sanitizeMediaUrl(preview);
		}
		return "";
	}

	private boolean isLikelyMediaUrl(String url) {
		String lower = url.toLowerCase(Locale.ROOT);
		return lower.contains("i.redd.it")
				|| lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg")
				|| lower.endsWith(".png")
				|| lower.endsWith(".gif")
				|| lower.contains("preview.redd.it");
	}

	private String inferMediaType(String mediaUrl) {
		String lower = mediaUrl.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".gif")) {
			return "gif";
		}
		return "image";
	}

	private String sanitizeMediaUrl(String rawUrl) {
		return rawUrl.replace("&amp;", "&");
	}

	private boolean isImageOnly(MemeItem item) {
		return "image".equals(item.type());
	}

	private boolean isCuratedSnapshot(MemeSnapshotEntity snapshot) {
		return "image".equalsIgnoreCase(snapshot.getMediaType());
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private List<MemeCategory> fallbackCatalog() {
		return List.of(
				new MemeCategory("gaming", "Gaming", "Weekly top image memes from major gaming meme communities", List.of()),
				new MemeCategory("work", "Work", "Weekly top image memes from office and developer humor communities", List.of()),
				new MemeCategory("kpop", "K-POP", "Weekly top image memes from K-pop fandom humor communities", List.of()),
				new MemeCategory("sports", "Sports", "Weekly top image memes from sports reaction communities", List.of())
		);
	}

	@Transactional
	protected void ensureDefaultCatalogConfig() {
		Map<String, MemeCategoryEntity> categoryByKey = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(MemeCategoryEntity::getCategoryKey, category -> category, (left, right) -> left, LinkedHashMap::new));

		Map<String, MemeCategoryEntity> existingCategories = categoryByKey;
		List<MemeCategoryEntity> categoriesToCreate = defaultCategorySeeds().stream()
				.filter(seed -> !existingCategories.containsKey(seed.key()))
				.map(seed -> new MemeCategoryEntity(seed.key(), seed.name(), seed.description(), seed.displayOrder(), true))
				.collect(Collectors.toList());
		if (!categoriesToCreate.isEmpty()) {
			categoryRepository.saveAll(categoriesToCreate);
			categoryByKey = categoryRepository.findAll().stream()
					.collect(Collectors.toMap(MemeCategoryEntity::getCategoryKey, category -> category, (left, right) -> left, LinkedHashMap::new));
		}

		Map<Long, String> categoryKeyById = categoryByKey.values().stream()
				.collect(Collectors.toMap(MemeCategoryEntity::getId, MemeCategoryEntity::getCategoryKey, (left, right) -> left, LinkedHashMap::new));
		Map<String, List<MemeSourceConfigEntity>> configsByCategory = sourceConfigRepository.findAll().stream()
				.collect(Collectors.groupingBy(
						config -> categoryKeyById.get(config.getCategory().getId()),
						LinkedHashMap::new,
						Collectors.toList()
				));

		List<MemeSourceConfigEntity> configsToCreate = new ArrayList<>();
		for (SourceSeed seed : defaultSourceSeeds()) {
			MemeCategoryEntity category = categoryByKey.get(seed.categoryKey());
			if (category == null) {
				continue;
			}
			boolean exists = configsByCategory.getOrDefault(seed.categoryKey(), List.of()).stream()
					.anyMatch(config -> config.getSourceType() == seed.sourceType()
							&& seed.queryValue().equals(config.getQueryValue()));
			if (!exists) {
				configsToCreate.add(new MemeSourceConfigEntity(
						category,
						seed.sourceType(),
						seed.queryValue(),
						seed.fetchLimit(),
						seed.regionCode(),
						seed.displayOrder(),
						true
				));
			}
		}
		if (!configsToCreate.isEmpty()) {
			sourceConfigRepository.saveAll(configsToCreate);
		}
	}

	private List<CategorySeed> defaultCategorySeeds() {
		return List.of(
				new CategorySeed("gaming", "Gaming", "Weekly top image memes from major gaming meme communities", 1),
				new CategorySeed("work", "Work", "Weekly top image memes from office and developer humor communities", 2),
				new CategorySeed("kpop", "K-POP", "Weekly top image memes from K-pop fandom humor communities", 3),
				new CategorySeed("sports", "Sports", "Weekly top image memes from sports reaction communities", 4)
		);
	}

	private List<SourceSeed> defaultSourceSeeds() {
		return List.of(
				new SourceSeed("gaming", SourceType.REDDIT, "gamingmemes", 10, null, 1),
				new SourceSeed("gaming", SourceType.REDDIT, "videogamememes", 10, null, 2),
				new SourceSeed("work", SourceType.REDDIT, "workmemes", 10, null, 1),
				new SourceSeed("work", SourceType.REDDIT, "ProgrammerHumor", 10, null, 2),
				new SourceSeed("kpop", SourceType.REDDIT, "kpoopheads", 10, null, 1),
				new SourceSeed("sports", SourceType.REDDIT, "sportsmemes", 10, null, 1),
				new SourceSeed("sports", SourceType.REDDIT, "nbamemes", 10, null, 2)
		);
	}

	private static final class InMemorySourceConfig extends MemeSourceConfigEntity {
		private final SourceType sourceType;
		private final String queryValue;
		private final int fetchLimit;
		private final String regionCode;

		private InMemorySourceConfig(SourceType sourceType, String queryValue, int fetchLimit, String regionCode) {
			this.sourceType = sourceType;
			this.queryValue = queryValue;
			this.fetchLimit = fetchLimit;
			this.regionCode = regionCode;
		}

		@Override
		public SourceType getSourceType() {
			return sourceType;
		}

		@Override
		public String getQueryValue() {
			return queryValue;
		}

		@Override
		public int getFetchLimit() {
			return fetchLimit;
		}

		@Override
		public String getRegionCode() {
			return regionCode;
		}
	}

	private record CategorySeed(String key, String name, String description, int displayOrder) {
	}

	private record SourceSeed(
			String categoryKey,
			SourceType sourceType,
			String queryValue,
			int fetchLimit,
			String regionCode,
			int displayOrder
	) {
	}
}
