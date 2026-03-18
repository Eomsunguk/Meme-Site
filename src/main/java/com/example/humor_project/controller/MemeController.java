package com.example.humor_project.controller;

import com.example.humor_project.model.CategoryDetailView;
import com.example.humor_project.model.MemeArchiveMonth;
import com.example.humor_project.model.MemeCategory;
import com.example.humor_project.model.MemeItem;
import com.example.humor_project.service.MemeCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class MemeController {
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
	private static final Logger log = LoggerFactory.getLogger(MemeController.class);
	private static final String SITE_NAME = "Meme Pulse";

	private final MemeCatalogService memeCatalogService;

	public MemeController(MemeCatalogService memeCatalogService) {
		this.memeCatalogService = memeCatalogService;
	}

	@GetMapping("/")
	public String index(Model model) {
		try {
			LocalDate lastUpdatedDate = memeCatalogService.getLastUpdatedDate();
			LocalDate nextUpdatedDate = memeCatalogService.getNextUpdateDate();
			List<MemeCategory> categories = memeCatalogService.getTrendingCategories();
			List<MemeArchiveMonth> archives = memeCatalogService.getArchiveMonths();
			model.addAttribute("categories", categories);
			model.addAttribute("archives", archives);
			model.addAttribute("categoryViews", buildCategoryViews(categories, archives));
			model.addAttribute("lastUpdatedText", lastUpdatedDate.format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("nextUpdatedText", nextUpdatedDate.format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("currentMonthLabel", lastUpdatedDate.format(MONTH_FORMATTER));
			model.addAttribute("pageTitle", SITE_NAME + " | Weekly Meme Snapshot Archive");
			model.addAttribute("metaDescription", "Browse curated weekly meme snapshots by category with archive views, source context, and searchable tags.");
		} catch (Exception exception) {
			LocalDate today = LocalDate.now();
			log.error("Failed to render meme index page", exception);
			model.addAttribute("categories", List.of());
			model.addAttribute("archives", List.of());
			model.addAttribute("categoryViews", List.of());
			model.addAttribute("lastUpdatedText", today.format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("nextUpdatedText", today.plusWeeks(1).format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("currentMonthLabel", today.format(MONTH_FORMATTER));
			model.addAttribute("pageTitle", SITE_NAME + " | Weekly Meme Snapshot Archive");
			model.addAttribute("metaDescription", "Browse curated weekly meme snapshots by category with archive views, source context, and searchable tags.");
		}
		return "index";
	}

	@GetMapping("/about")
	public String about(Model model) {
		model.addAttribute("pageTitle", "About | " + SITE_NAME);
		model.addAttribute("metaDescription", "Learn how Meme Pulse curates weekly meme snapshots, organizes archives, and prioritizes clear source context.");
		return "about";
	}

	@GetMapping("/category/{key}")
	public String categoryDetail(@PathVariable String key, Model model) {
		List<MemeCategory> categories = memeCatalogService.getTrendingCategories();
		List<MemeArchiveMonth> archives = memeCatalogService.getArchiveMonths();
		Optional<MemeCategory> category = categories.stream()
				.filter(item -> item.key().equalsIgnoreCase(key))
				.findFirst();
		if (category.isEmpty()) {
			model.addAttribute("pageTitle", "Category Not Found | " + SITE_NAME);
			model.addAttribute("metaDescription", "The requested meme category does not exist in the current Meme Pulse archive.");
			return "404";
		}

		CategoryDetailView detailView = buildCategoryView(category.get(), archives);
		model.addAttribute("pageTitle", detailView.name() + " Meme Archive | " + SITE_NAME);
		model.addAttribute("metaDescription", "Explore the " + detailView.name() + " meme archive with editorial context, tag trends, and weekly snapshots.");
		model.addAttribute("detail", detailView);
		model.addAttribute("allCategories", categories);
		return "category-detail";
	}

	@GetMapping("/contact")
	public String contact(Model model) {
		model.addAttribute("pageTitle", "Contact | " + SITE_NAME);
		model.addAttribute("metaDescription", "Contact Meme Pulse about site feedback, source corrections, and archive issues.");
		return "contact";
	}

	@GetMapping("/privacy")
	public String privacy(Model model) {
		model.addAttribute("pageTitle", "Privacy | " + SITE_NAME);
		model.addAttribute("metaDescription", "Review the Meme Pulse privacy summary, data handling notes, and external link disclosures.");
		return "privacy";
	}

	@GetMapping("/editorial-policy")
	public String editorialPolicy(Model model) {
		model.addAttribute("pageTitle", "Editorial Policy | " + SITE_NAME);
		model.addAttribute("metaDescription", "Review the Meme Pulse editorial policy, inclusion standards, correction flow, and archive principles.");
		return "editorial-policy";
	}

	@GetMapping("/health")
	@ResponseBody
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}

	private List<CategoryDetailView> buildCategoryViews(List<MemeCategory> categories, List<MemeArchiveMonth> archives) {
		return categories.stream()
				.map(category -> buildCategoryView(category, archives))
				.collect(Collectors.toList());
	}

	private CategoryDetailView buildCategoryView(MemeCategory category, List<MemeArchiveMonth> archives) {
		List<MemeArchiveMonth> relatedArchives = archives.stream()
				.map(archive -> new MemeArchiveMonth(
						archive.label(),
						archive.snapshotDate(),
						archive.categories().stream()
								.filter(item -> item.key().equals(category.key()))
								.collect(Collectors.toList())
				))
				.filter(archive -> !archive.categories().isEmpty())
				.collect(Collectors.toList());

		Map<String, Long> sourceCounts = category.items().stream()
				.collect(Collectors.groupingBy(MemeItem::source, LinkedHashMap::new, Collectors.counting()));
		String topSourceLabel = sourceCounts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(entry -> entry.getKey() + " (" + entry.getValue() + " items)")
				.orElse("No current source data");

		List<String> topTags = category.items().stream()
				.flatMap(item -> splitTags(item.tags()).stream())
				.collect(Collectors.groupingBy(tag -> tag, LinkedHashMap::new, Collectors.counting()))
				.entrySet()
				.stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(5)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		String trendSummary = buildTrendSummary(category, topTags, relatedArchives.size());
		String whyItMatters = buildWhyItMatters(category);
		String browsingTip = buildBrowsingTip(category, topTags);

		return new CategoryDetailView(
				category.key(),
				category.name(),
				category.description(),
				trendSummary,
				whyItMatters,
				browsingTip,
				topSourceLabel,
				category.items().size(),
				relatedArchives.size(),
				topTags,
				editorialChecksFor(category),
				category.items(),
				relatedArchives
		);
	}

	private List<String> splitTags(String rawTags) {
		if (rawTags == null || rawTags.isBlank()) {
			return List.of();
		}
		List<String> tags = new ArrayList<>();
		for (String tag : rawTags.split(",")) {
			String normalized = tag.trim().toLowerCase(Locale.ENGLISH);
			if (!normalized.isBlank()) {
				tags.add(normalized);
			}
		}
		return tags;
	}

	private String buildTrendSummary(MemeCategory category, List<String> topTags, int archiveCount) {
		String tagsText = topTags.isEmpty()
				? "reaction formats and recurring community jokes"
				: String.join(", ", topTags);
		return category.name() + " currently highlights " + category.items().size()
				+ " curated items, with repeated themes around " + tagsText
				+ ". The archive keeps " + archiveCount + " earlier weekly snapshots so visitors can compare how the jokes shift over time.";
	}

	private String buildWhyItMatters(MemeCategory category) {
		return switch (category.key()) {
			case "gaming" -> "Gaming meme formats move fast across releases, balance patches, and platform drama, so weekly curation helps visitors revisit context instead of relying on a single day of posts.";
			case "work" -> "Work humor is useful because it turns recurring office and developer pain points into searchable themes that readers can compare across weeks and workplace situations.";
			case "kpop" -> "K-pop meme culture changes with comeback cycles, fandom in-jokes, and stage moments, so an archive adds context beyond one-off viral images.";
			case "sports" -> "Sports reaction memes swing with scores, coaching decisions, and rivalry moments, and a weekly archive makes those emotion spikes easier to review.";
			default -> "A curated archive adds value by summarizing, grouping, and preserving items that would otherwise disappear into a fast-moving feed.";
		};
	}

	private String buildBrowsingTip(MemeCategory category, List<String> topTags) {
		String firstTag = topTags.isEmpty() ? "reaction" : topTags.get(0);
		return "Start with the current snapshot, then search by a tag like '" + firstTag
				+ "' to isolate a theme before opening original source links from the " + category.name() + " lane.";
	}

	private List<String> editorialChecksFor(MemeCategory category) {
		return List.of(
				"Each " + category.name() + " card keeps a source link and summary so users know what they are opening.",
				"The page groups related items in one destination instead of scattering near-duplicate posts across many thin URLs.",
				"Archive sections stay visible only when historical snapshots exist, keeping empty sections out of the browsing path."
		);
	}
}
