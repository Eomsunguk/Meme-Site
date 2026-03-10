package com.example.humor_project.persistence.entity;

import com.example.humor_project.model.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "meme_batch")
public class MemeBatchEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "run_date", nullable = false)
	private LocalDate runDate;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private BatchStatus status;

	@Column(length = 500)
	private String message;

	@Column(name = "item_count", nullable = false)
	private int itemCount;

	protected MemeBatchEntity() {
	}

	public MemeBatchEntity(LocalDate runDate, Instant startedAt, BatchStatus status) {
		this.runDate = runDate;
		this.startedAt = startedAt;
		this.status = status;
	}

	public Long getId() {
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
