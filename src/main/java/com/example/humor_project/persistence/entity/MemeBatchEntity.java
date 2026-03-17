package com.example.humor_project.persistence.entity;

import com.example.humor_project.model.BatchStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document("meme_batches")
public class MemeBatchEntity {

	@Id
	private String id;
	private LocalDate runDate;
	private Instant startedAt;
	private Instant endedAt;
	private BatchStatus status;
	private String message;
	private int itemCount;

	protected MemeBatchEntity() {
	}

	public MemeBatchEntity(LocalDate runDate, Instant startedAt, BatchStatus status) {
		this.runDate = runDate;
		this.startedAt = startedAt;
		this.status = status;
	}

	public String getId() {
		return id;
	}

	public LocalDate getRunDate() {
		return runDate;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public BatchStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}

	public int getItemCount() {
		return itemCount;
	}

	public void markFinished(BatchStatus status, String message, int itemCount, Instant endedAt) {
		this.status = status;
		this.message = message;
		this.itemCount = itemCount;
		this.endedAt = endedAt;
	}
}
