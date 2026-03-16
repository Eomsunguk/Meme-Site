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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MemeCatalogService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_ITEMS_PER_CATEGORY = 12;
	private static final int MAX_ARCHIVE_MONTHS = 6;
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	private final MemeCategoryRepository categoryRepository;
	private final MemeSourceConfigRepository sourceConfigRepository;
	private final MemeBatchRepository batchRepository;
	private final MemeSnapshotRepository snapshotRepository;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	@Value("${meme.youtube.api-key:}")
	private String youtubeApiKey;

	@Value("${meme.youtube.region-code:KR}")
	private String youtubeRegionCode;

	@Value("${meme.x.bearer-token:}")
	private String xBearerToken;

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
		loadPersistedCatalog();
		if (warmUpEnabled) {
			refreshCatalogSafely(LocalDate.now(KST));
		}
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
		Instant startedAt = Instant.now();
		MemeBatchEntity batch = batchRepository.save(new MemeBatchEntity(refreshDate, startedAt, BatchStatus.RUNNING));
		try {
			List<MemeCategoryEntity> categories = categoryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
			List<MemeSourceConfigEntity> configs = sourceConfigRepository.findAllByActiveTrueOrderByCategory_DisplayOrderAscDisplayOrderAscIdAsc();
			List<MemeCategory> liveCatalog = buildLiveCatalog(categories, configs);
			List<MemeCategory> mergedCatalog = mergeWithLatestSuccessfulCatalog(categories, liveCatalog);
			int itemCount = mergedCatalog.stream().mapToInt(category -> category.items().size()).sum();
			if (itemCount > 0) {
				persistSuccessfulBatch(batch, categories, mergedCatalog, Instant.now());
				cachedCategories = mergedCatalog;
				lastUpdatedDate = refreshDate;
				loadArchiveMonths(categories);
				return;
			}
			saveFailedBatch(batch, "No playable meme items fetched", Instant.now());
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
				.collect(Collectors.groupingBy(config -> config.getCategory().getId(), LinkedHashMap::new, Collectors.toList()));

		for (MemeCategoryEntity category : categories) {
			for (MemeSourceConfigEntity config : configsByCategory.getOrDefault(category.getId(), List.of())) {
				bucket.get(category.getId()).addAll(fetchItems(config));
			}
		}

		List<MemeCategory> result = new ArrayList<>();
		for (MemeCategoryEntity category : categories) {
			List<MemeItem> selected = bucket.get(category.getId()).stream()
					.filter(this::isPlayableMedia)
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
		return switch (config.getSourceType()) {
			case REDDIT -> fetchRedditHot(config.getQueryValue(), config.getFetchLimit());
			case YOUTUBE -> {
				if (youtubeApiKey.isBlank()) {
					yield List.of();
				}
				String regionCode = config.getRegionCode() == null || config.getRegionCode().isBlank()
						? youtubeRegionCode
						: config.getRegionCode();
				yield fetchYoutubePopular(config.getQueryValue(), regionCode, config.getFetchLimit());
			}
			case X -> {
				if (xBearerToken.isBlank()) {
					yield List.of();
				}
				yield fetchXPopular(config.getQueryValue(), config.getFetchLimit());
			}
		};
	}

	private List<MemeCategory> mergeWithLatestSuccessfulCatalog(List<MemeCategoryEntity> categories, List<MemeCategory> liveCatalog) {
		Optional<MemeBatchEntity> latestBatch = batchRepository.findTopByStatusOrderByRunDateDescStartedAtDesc(BatchStatus.SUCCESS);
		if (latestBatch.isEmpty()) {
			return liveCatalog;
		}

		Map<String, MemeCategory> previousByKey = mapPersistedCatalog(categories, loadSnapshots(latestBatch.get().getId())).stream()
				.collect(Collectors.toMap(MemeCategory::key, category -> category, (left, right) -> left, LinkedHashMap::new));
		Map<String, MemeCategory> liveByKey = liveCatalog.stream()
				.collect(Collectors.toMap(MemeCategory::key, category -> category, (left, right) -> left, LinkedHashMap::new));

		return categories.stream()
				.map(categoryEntity -> {
					MemeCategory live = liveByKey.get(categoryEntity.getCategoryKey());
					if (live != null && !live.items().isEmpty()) {
						return live;
					}
					MemeCategory previous = previousByKey.get(categoryEntity.getCategoryKey());
					if (previous != null && !previous.items().isEmpty()) {
						return previous;
					}
					return live != null
							? live
							: new MemeCategory(
									categoryEntity.getCategoryKey(),
									categoryEntity.getName(),
									categoryEntity.getDescription(),
									List.of()
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
		Map<YearMonth, MemeBatchEntity> latestBatchByMonth = new LinkedHashMap<>();
		for (MemeBatchEntity batch : batchRepository.findAllByStatusOrderByRunDateDescStartedAtDesc(BatchStatus.SUCCESS)) {
			YearMonth month = YearMonth.from(batch.getRunDate());
			latestBatchByMonth.putIfAbsent(month, batch);
			if (latestBatchByMonth.size() >= MAX_ARCHIVE_MONTHS + 1) {
				break;
			}
		}

		List<MemeArchiveMonth> archives = new ArrayList<>();
		boolean skipCurrent = true;
		for (MemeBatchEntity batch : latestBatchByMonth.values()) {
			if (skipCurrent) {
				skipCurrent = false;
				continue;
			}
			archives.add(new MemeArchiveMonth(
					batch.getRunDate().format(MONTH_FORMATTER),
					batch.getRunDate(),
					mapPersistedCatalog(categories, loadSnapshots(batch.getId()))
			));
		}
		cachedArchiveMonths = archives;
	}

	private List<MemeSnapshotEntity> loadSnapshots(Long batchId) {
		return snapshotRepository.findAllByBatch_IdOrderByCategory_DisplayOrderAscRankOrderAscIdAsc(batchId);
	}

	private List<MemeItem> fetchRedditHot(String subreddit, int limit) {
		List<MemeItem> items = new ArrayList<>();
		String url = "https://www.reddit.com/r/" + subreddit + "/hot.json?raw_json=1&limit=" + limit;
		try {
			JsonNode root = getJson(url, Map.of("User-Agent", "meme-pulse/1.0"));
			for (JsonNode child : root.path("data").path("children")) {
				JsonNode data = child.path("data");
				String mediaUrl = resolveRedditMediaUrl(data);
				if (mediaUrl.isBlank()) {
					continue;
				}

				String type = inferMediaType(mediaUrl);
				long score = data.path("score").asLong(0);
				long comments = data.path("num_comments").asLong(0);
				String title = data.path("title").asText("Untitled");
				String permalink = data.path("permalink").asText("");
				String sourceUrl = permalink.isBlank() ? "https://www.reddit.com/r/" + subreddit
						: "https://www.reddit.com" + permalink;

				items.add(new MemeItem(
						title,
						type,
						mediaUrl,
						false,
						sourceUrl,
						"Reddit score " + score + " / comments " + comments + " / fetched " + DATE_FORMATTER.format(LocalDate.now(KST)),
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

	private List<MemeItem> fetchYoutubePopular(String query, String regionCode, int limit) {
		List<MemeItem> items = new ArrayList<>();
		try {
			String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
			String publishedAfter = Instant.now().minus(Duration.ofDays(30)).toString();
			String searchUrl = "https://www.googleapis.com/youtube/v3/search"
					+ "?part=snippet&type=video&maxResults=" + limit
					+ "&order=viewCount&regionCode=" + regionCode
					+ "&publishedAfter=" + URLEncoder.encode(publishedAfter, StandardCharsets.UTF_8)
					+ "&q=" + encodedQuery
					+ "&key=" + youtubeApiKey;

			JsonNode search = getJson(searchUrl, Map.of());
			List<String> videoIds = new ArrayList<>();
			for (JsonNode item : search.path("items")) {
				String id = item.path("id").path("videoId").asText("");
				if (!id.isBlank()) {
					videoIds.add(id);
				}
			}

			Map<String, Long> views = fetchYoutubeViews(videoIds);
			for (JsonNode item : search.path("items")) {
				String videoId = item.path("id").path("videoId").asText("");
				if (videoId.isBlank()) {
					continue;
				}
				String title = item.path("snippet").path("title").asText("YouTube meme");
				long viewCount = views.getOrDefault(videoId, 0L);
				items.add(new MemeItem(
						title,
						"video",
						"https://www.youtube.com/embed/" + videoId,
						true,
						"https://www.youtube.com/watch?v=" + videoId,
						"YouTube views " + viewCount,
						"youtube, meme",
						"YouTube",
						viewCount
				));
			}
		} catch (Exception ignored) {
			return List.of();
		}
		return items;
	}

	private Map<String, Long> fetchYoutubeViews(List<String> videoIds) throws IOException, InterruptedException {
		if (videoIds.isEmpty()) {
			return Map.of();
		}
		String ids = URLEncoder.encode(String.join(",", videoIds), StandardCharsets.UTF_8);
		String url = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id=" + ids + "&key=" + youtubeApiKey;
		JsonNode root = getJson(url, Map.of());
		Map<String, Long> result = new ConcurrentHashMap<>();
		for (JsonNode item : root.path("items")) {
			String id = item.path("id").asText("");
			long views = item.path("statistics").path("viewCount").asLong(0L);
			if (!id.isBlank()) {
				result.put(id, views);
			}
		}
		return result;
	}

	private List<MemeItem> fetchXPopular(String query, int limit) {
		List<MemeItem> items = new ArrayList<>();
		try {
			String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
			String url = "https://api.x.com/2/tweets/search/recent"
					+ "?query=" + encodedQuery
					+ "&max_results=" + Math.min(Math.max(limit, 10), 100)
					+ "&expansions=attachments.media_keys"
					+ "&tweet.fields=public_metrics,text,attachments"
					+ "&media.fields=media_key,type,url,preview_image_url,variants";

			JsonNode root = getJson(url, Map.of("Authorization", "Bearer " + xBearerToken));
			Map<String, JsonNode> mediaByKey = new LinkedHashMap<>();
			for (JsonNode media : root.path("includes").path("media")) {
				String key = media.path("media_key").asText("");
				if (!key.isBlank()) {
					mediaByKey.put(key, media);
				}
			}

			for (JsonNode tweet : root.path("data")) {
				JsonNode keys = tweet.path("attachments").path("media_keys");
				if (!keys.isArray() || keys.isEmpty()) {
					continue;
				}
				String mediaKey = keys.get(0).asText("");
				JsonNode media = mediaByKey.get(mediaKey);
				if (media == null) {
					continue;
				}

				String mediaType = media.path("type").asText("");
				String mediaUrl = "";
				String type = "image";
				if ("photo".equals(mediaType)) {
					mediaUrl = media.path("url").asText("");
				} else if ("animated_gif".equals(mediaType) || "video".equals(mediaType)) {
					mediaUrl = resolveXVideoVariant(media.path("variants"));
					type = mediaType.equals("animated_gif") ? "gif" : "video";
				}
				if (mediaUrl.isBlank()) {
					continue;
				}

				long likes = tweet.path("public_metrics").path("like_count").asLong(0);
				long reposts = tweet.path("public_metrics").path("retweet_count").asLong(0);
				long popularity = likes + reposts;
				String id = tweet.path("id").asText("");
				String text = tweet.path("text").asText("X meme");
				String shortText = text.length() > 120 ? text.substring(0, 120) + "..." : text;

				items.add(new MemeItem(
						shortText,
						type,
						mediaUrl,
						false,
						"https://x.com/i/web/status/" + id,
						"X likes " + likes + " / reposts " + reposts,
						"x, trending",
						"X",
						popularity
				));
			}
		} catch (Exception ignored) {
			return List.of();
		}
		return items;
	}

	private String resolveXVideoVariant(JsonNode variants) {
		String best = "";
		int bestBitrate = -1;
		for (JsonNode variant : variants) {
			String contentType = variant.path("content_type").asText("");
			if (!"video/mp4".equals(contentType)) {
				continue;
			}
			int bitrate = variant.path("bit_rate").asInt(0);
			String url = variant.path("url").asText("");
			if (!url.isBlank() && bitrate >= bestBitrate) {
				bestBitrate = bitrate;
				best = url;
			}
		}
		return best;
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
		String fallbackVideo = data.path("media").path("reddit_video").path("fallback_url").asText("");
		if (!fallbackVideo.isBlank()) {
			return sanitizeMediaUrl(fallbackVideo);
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
				|| lower.contains("v.redd.it")
				|| lower.endsWith(".jpg")
				|| lower.endsWith(".jpeg")
				|| lower.endsWith(".png")
				|| lower.endsWith(".gif")
				|| lower.endsWith(".mp4")
				|| lower.endsWith(".webm")
				|| lower.contains("preview.redd.it");
	}

	private String inferMediaType(String mediaUrl) {
		String lower = mediaUrl.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".gif")) {
			return "gif";
		}
		if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.contains("v.redd.it")) {
			return "video";
		}
		return "image";
	}

	private String sanitizeMediaUrl(String rawUrl) {
		return rawUrl.replace("&amp;", "&");
	}

	private boolean isPlayableMedia(MemeItem item) {
		return "image".equals(item.type());
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

	private List<MemeCategory> fallbackCatalog() {
		return List.of(
				new MemeCategory("gaming", "Gaming", "Fallback card when API fetch fails", List.of(
						new MemeItem(
								"Fallback Meme",
								"image",
								"https://images.unsplash.com/photo-1542751371-adc38448a05e?auto=format&fit=crop&w=1200&q=80",
								false,
								"https://unsplash.com/",
								"Default fallback card shown when live trend APIs fail",
								"fallback",
								"Local",
								1
						)
				)),
				new MemeCategory("work", "Work", "Office and work-life meme trends", List.of(
						new MemeItem(
								"When the sprint board says 'just one quick fix'",
								"image",
								"/img/archive/work-2026-03.svg",
								false,
								"https://www.reddit.com/r/ProgrammerHumor/",
								"Starter card kept until live sources return a newer work meme batch.",
								"work, office, sprint",
								"Archive Seed",
								1
						)
				)),
				new MemeCategory("kpop", "K-POP", "K-pop fandom meme trends", List.of(
						new MemeItem(
								"Bias changed again after one fancam",
								"image",
								"/img/archive/kpop-2026-03.svg",
								false,
								"https://www.reddit.com/r/kpoopheads/",
								"Starter card kept until live sources return a newer K-pop meme batch.",
								"kpop, fandom, fancam",
								"Archive Seed",
								1
						)
				)),
				new MemeCategory("sports", "Sports", "Sports reaction meme trends", List.of(
						new MemeItem(
								"Team rebuilding for the fifth year in a row",
								"image",
								"/img/archive/sports-2026-03.svg",
								false,
								"https://www.reddit.com/r/sportsmemes/",
								"Starter card kept until live sources return a newer sports meme batch.",
								"sports, rebuild, reaction",
								"Archive Seed",
								1
						)
				))
		);
	}
}
