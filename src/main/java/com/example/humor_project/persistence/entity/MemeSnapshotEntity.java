package com.example.humor_project.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("meme_snapshots")
public class MemeSnapshotEntity {

	@Id
	private String id;
	private String batchId;
	private String categoryKey;
	private String title;
	private String mediaType;
	private String mediaUrl;
	private String sourceUrl;
	private String summary;
	private String tags;
	private String source;
	private long popularity;
	private int rankOrder;

	protected MemeSnapshotEntity() {
	}

	public MemeSnapshotEntity(
			String batchId,
			String categoryKey,
			String title,
			String mediaType,
			String mediaUrl,
			String sourceUrl,
			String summary,
			String tags,
			String source,
			long popularity,
			int rankOrder
	) {
		this.batchId = batchId;
		this.categoryKey = categoryKey;
		this.title = title;
		this.mediaType = mediaType;
		this.mediaUrl = mediaUrl;
		this.sourceUrl = sourceUrl;
		this.summary = summary;
		this.tags = tags;
		this.source = source;
		this.popularity = popularity;
		this.rankOrder = rankOrder;
	}

	public String getCategoryKey() {
		return categoryKey;
	}

	public String getBatchId() {
		return batchId;
	}

	public String getTitle() {
		return title;
	}

	public String getMediaType() {
		return mediaType;
	}

	public String getMediaUrl() {
		return mediaUrl;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getSummary() {
		return summary;
	}

	public String getTags() {
		return tags;
	}

	public String getSource() {
		return source;
	}

	public long getPopularity() {
		return popularity;
	}

	public int getRankOrder() {
		return rankOrder;
	}
}
