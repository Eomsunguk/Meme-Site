package com.example.humor_project.persistence.entity;

import com.example.humor_project.model.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "meme_source_config")
public class MemeSourceConfigEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private MemeCategoryEntity category;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 32)
	private SourceType sourceType;

	@Column(name = "query_value", nullable = false, length = 255)
	private String queryValue;

	@Column(name = "fetch_limit", nullable = false)
	private int fetchLimit;

	@Column(name = "region_code", length = 16)
	private String regionCode;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(nullable = false)
	private boolean active;

	protected MemeSourceConfigEntity() {
	}

	public MemeSourceConfigEntity(
			MemeCategoryEntity category,
			SourceType sourceType,
			String queryValue,
			int fetchLimit,
			String regionCode,
			int displayOrder,
			boolean active
	) {
		this.category = category;
		this.sourceType = sourceType;
		this.queryValue = queryValue;
		this.fetchLimit = fetchLimit;
		this.regionCode = regionCode;
		this.displayOrder = displayOrder;
		this.active = active;
	}

	public MemeCategoryEntity getCategory() {
		return category;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public String getQueryValue() {
		return queryValue;
	}

	public int getFetchLimit() {
		return fetchLimit;
	}

	public String getRegionCode() {
		return regionCode;
	}

	public int getDisplayOrder() {
		return displayOrder;
	}

	public boolean isActive() {
		return active;
	}
}
