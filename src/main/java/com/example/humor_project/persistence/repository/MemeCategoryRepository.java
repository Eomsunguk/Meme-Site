package com.example.humor_project.persistence.repository;

import com.example.humor_project.persistence.entity.MemeCategoryEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemeCategoryRepository extends MongoRepository<MemeCategoryEntity, String> {

	List<MemeCategoryEntity> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
}
