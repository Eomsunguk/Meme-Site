package com.example.humor_project.service;

import com.example.humor_project.model.MemeCategory;
import com.example.humor_project.model.MemeItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MemeCatalogService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int MAX_ITEMS_PER_CATEGORY = 12;
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

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

	private volatile List<MemeCategory> cachedCategories = fallbackCatalog();
	private volatile LocalDate lastUpdatedDate = LocalDate.now(KST);

	private final Map<String, List<String>> categoryToSubreddits = Map.of(
			"gaming", List.of("gamingmemes", "gaming"),
			"work", List.of("workmemes", "ProgrammerHumor"),
			"kpop", List.of("kpoopheads", "kpopthoughts"),
			"sports", List.of("sportsmemes", "soccer")
	);

	private final Map<String, String> categoryNames = Map.of(
			"gaming", "Gaming",
			"work", "Work",
			"kpop", "K-POP",
			"sports", "Sports"
	);

	private final Map<String, String> categoryDescriptions = Map.of(
			"gaming", "Trending gaming memes",
			"work", "Office and work-life meme trends",
			"kpop", "K-pop fandom meme trends",
			"sports", "Sports reaction meme trends"
	);

	private final Map<String, String> youtubeQueries = Map.of(
			"gaming", "gaming meme",
			"work", "office meme",
			"kpop", "kpop meme",
			"sports", "sports meme"
	);

	private final Map<String, String> xQueries = Map.of(
			"gaming", "(gaming meme) has:media -is:retweet",
			"work", "(office meme OR monday meme) has:media -is:retweet",
			"kpop", "(kpop meme) has:media -is:retweet",
			"sports", "(sports meme OR football meme) has:media -is:retweet"
	);

	@PostConstruct
	public void warmUp() {
		refreshCatalogSafely(LocalDate.now(KST));
	}

	public List<MemeCategory> getTrendingCategories() {
		return cachedCategories;
	}

	public LocalDate getLastUpdatedDate() {
		return lastUpdatedDate;
	}

	public LocalDate getNextUpdateDate() {
		return lastUpdatedDate.plusWeeks(1);
	}

	@Scheduled(cron = "${meme.refresh.cron:0 0 0 * * MON}", zone = "Asia/Seoul")
	public void refreshWeekly() {
		refreshCatalogSafely(LocalDate.now(KST));
	}

	private synchronized void refreshCatalogSafely(LocalDate refreshDate) {
		try {
			List<MemeCategory> live = buildLiveCatalog();
			boolean hasAny = live.stream().anyMatch(category -> !category.items().isEmpty());
			if (hasAny) {
				cachedCategories = live;
				lastUpdatedDate = refreshDate;
			}
		} catch (Exception ignored) {
			// Keep serving the previous snapshot.
		}
	}

	private List<MemeCategory> buildLiveCatalog() {
		Map<String, List<MemeItem>> bucket = new ConcurrentHashMap<>();
		for (String key : categoryToSubreddits.keySet()) {
			bucket.put(key, new ArrayList<>());
		}

		for (Map.Entry<String, List<String>> entry : categoryToSubreddits.entrySet()) {
			String category = entry.getKey();
			for (String subreddit : entry.getValue()) {
				bucket.get(category).addAll(fetchRedditHot(subreddit, 20));
			}
		}

		if (!youtubeApiKey.isBlank()) {
			for (Map.Entry<String, String> entry : youtubeQueries.entrySet()) {
				bucket.get(entry.getKey()).addAll(fetchYoutubePopular(entry.getValue(), 5));
			}
		}
		if (!xBearerToken.isBlank()) {
			for (Map.Entry<String, String> entry : xQueries.entrySet()) {
				bucket.get(entry.getKey()).addAll(fetchXPopular(entry.getValue(), 8));
			}
		}

		List<MemeCategory> result = new ArrayList<>();
		for (String key : categoryToSubreddits.keySet()) {
			List<MemeItem> selected = bucket.get(key).stream()
					.filter(this::isPlayableMedia)
					.sorted(Comparator.comparingLong(MemeItem::popularity).reversed())
					.limit(MAX_ITEMS_PER_CATEGORY)
					.collect(Collectors.toList());

			result.add(new MemeCategory(
					key,
					categoryNames.getOrDefault(key, key),
					categoryDescriptions.getOrDefault(key, ""),
					selected
			));
		}
		return result;
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
						"Reddit score " + score + " / comments " + comments,
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

	private List<MemeItem> fetchYoutubePopular(String query, int limit) {
		List<MemeItem> items = new ArrayList<>();
		try {
			String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
			String publishedAfter = Instant.now().minus(Duration.ofDays(30)).toString();
			String searchUrl = "https://www.googleapis.com/youtube/v3/search"
					+ "?part=snippet&type=video&maxResults=" + limit
					+ "&order=viewCount&regionCode=" + youtubeRegionCode
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
			Map<String, JsonNode> mediaByKey = new HashMap<>();
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
				boolean embed = false;
				String type = "image";
				if ("photo".equals(mediaType)) {
					mediaUrl = media.path("url").asText("");
					type = "image";
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
						embed,
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
				))
		);
	}
}
