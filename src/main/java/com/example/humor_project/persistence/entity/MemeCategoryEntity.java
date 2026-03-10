package com.example.humor_project.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meme_category")
public class MemeCategoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "category_key", nullable = false, unique = true, length = 64)
	private String categoryKey;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 255)
	private String description;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(nullable = false)
	private boolean active;

	protected MemeCategoryEntity() {
	}

	public Long getId() {
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
