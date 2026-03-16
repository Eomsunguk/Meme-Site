package com.example.humor_project.model;

import java.time.LocalDate;
import java.util.List;

public record MemeArchiveMonth(
		String label,
		LocalDate snapshotDate,
		List<MemeCategory> categories
) {
}
