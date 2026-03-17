package com.example.humor_project.persistence.repository;

import com.example.humor_project.persistence.entity.MemeSnapshotEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemeSnapshotRepository extends JpaRepository<MemeSnapshotEntity, Long> {

	@EntityGraph(attributePaths = "category")
	List<MemeSnapshotEntity> findAllByBatch_IdOrderByCategory_DisplayOrderAscRankOrderAscIdAsc(Long batchId);
}
