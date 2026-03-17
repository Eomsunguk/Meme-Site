package com.example.humor_project.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("meme_categories")
public class MemeCategoryEntity {

	@Id
	private String id;

	@Indexed(unique = true)
	private String categoryKey;
	private String name;
	private String description;
	private int displayOrder;
	private boolean active;

	protected MemeCategoryEntity() {
	}

	public MemeCategoryEntity(String categoryKey, String name, String description, int displayOrder, boolean active) {
		this.categoryKey = categoryKey;
		this.name = name;
		this.description = description;
		this.displayOrder = displayOrder;
		this.active = active;
	}

	public String getId() {
		return id;
	}

	public String getCategoryKey() {
		return categoryKey;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public boolean isActive() {
		return active;
	}
}
