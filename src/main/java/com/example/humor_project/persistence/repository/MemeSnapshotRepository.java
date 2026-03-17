package com.example.humor_project.persistence.repository;

import com.example.humor_project.persistence.entity.MemeSnapshotEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemeSnapshotRepository extends MongoRepository<MemeSnapshotEntity, String> {

	List<MemeSnapshotEntity> findAllByBatchIdOrderByCategoryKeyAscRankOrderAscIdAsc(String batchId);
}
