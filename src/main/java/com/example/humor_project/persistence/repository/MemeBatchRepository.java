package com.example.humor_project.persistence.repository;

import com.example.humor_project.model.BatchStatus;
import com.example.humor_project.persistence.entity.MemeBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemeBatchRepository extends JpaRepository<MemeBatchEntity, Long> {

	Optional<MemeBatchEntity> findTopByStatusOrderByRunDateDescStartedAtDesc(BatchStatus status);

	List<MemeBatchEntity> findAllByStatusOrderByRunDateDescStartedAtDesc(BatchStatus status);
}
