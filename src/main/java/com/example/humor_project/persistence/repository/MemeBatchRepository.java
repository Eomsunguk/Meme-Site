package com.example.humor_project.persistence.repository;

import com.example.humor_project.model.BatchStatus;
import com.example.humor_project.persistence.entity.MemeBatchEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MemeBatchRepository extends MongoRepository<MemeBatchEntity, String> {

	Optional<MemeBatchEntity> findTopByStatusOrderByRunDateDescStartedAtDesc(BatchStatus status);

	List<MemeBatchEntity> findAllByStatusOrderByRunDateDescStartedAtDesc(BatchStatus status);
}
