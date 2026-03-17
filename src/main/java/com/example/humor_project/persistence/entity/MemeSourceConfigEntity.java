package com.example.humor_project.persistence.entity;

import com.example.humor_project.model.SourceType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("meme_source_configs")
public class MemeSourceConfigEntity {

	@Id
	private String id;
	private String categoryKey;
	private SourceType sourceType;
	private String queryValue;
	private int fetchLimit;
	private String regionCode;
	private int displayOrder;
	private boolean active;

	protected MemeSourceConfigEntity() {
	}

	public MemeSourceConfigEntity(
			String categoryKey,
			SourceType sourceType,
			String queryValue,
			int fetchLimit,
			String regionCode,
			int displayOrder,
			boolean active
	) {
		this.categoryKey = categoryKey;
		this.sourceType = sourceType;
		this.queryValue = queryValue;
		this.fetchLimit = fetchLimit;
		this.regionCode = regionCode;
		this.displayOrder = displayOrder;
		this.active = active;
	}

	public String getId() {
		return id;
	}

	public String getCategoryKey() {
		return categoryKey;
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
