package com.example.humor_project.model;

import java.util.List;

public record MemeCategory(
		String key,
		String name,
		String description,
		List<MemeItem> items
) {
}
