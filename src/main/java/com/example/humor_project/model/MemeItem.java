package com.example.humor_project.model;

public record MemeItem(
		String title,
		String type,
		String mediaUrl,
		boolean embed,
		String sourceUrl,
		String summary,
		String tags,
		String source,
		long popularity
) {
}
