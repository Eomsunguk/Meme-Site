package com.example.humor_project.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "meme_snapshot")
public class MemeSnapshotEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "batch_id", nullable = false)
	private MemeBatchEntity batch;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private MemeCategoryEntity category;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(name = "media_type", nullable = false, length = 32)
	private String mediaType;

	@Column(name = "media_url", nullable = false, length = 1000)
	private String mediaUrl;

	@Column(name = "source_url", nullable = false, length = 1000)
	private String sourceUrl;

	@Column(nullable = false, length = 500)
	private String summary;

	@Column(nullable = false, length = 255)
	private String tags;

	@Column(nullable = false, length = 64)
	private String source;

	@Column(nullable = false)
	private long popularity;

	@Column(name = "rank_order", nullable = false)
	private int rankOrder;

	protected MemeSnapshotEntity() {
	}

	public MemeSnapshotEntity(
			MemeBatchEntity batch,
			MemeCategoryEntity category,
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
		this.batch = batch;
		this.category = category;
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

	public MemeCategoryEntity getCategory() {
		return category;
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
}
