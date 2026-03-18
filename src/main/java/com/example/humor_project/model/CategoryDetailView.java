package com.example.humor_project.model;

import java.util.List;

public record CategoryDetailView(
		String key,
		String name,
		String description,
		String trendSummary,
		String whyItMatters,
		String browsingTip,
		String topSourceLabel,
		int itemCount,
		int archiveCount,
		List<String> topTags,
		List<String> editorialChecks,
		List<MemeItem> currentItems,
		List<MemeArchiveMonth> relatedArchives
) {
}
