package com.example.humor_project.persistence.repository;

import com.example.humor_project.persistence.entity.MemeSourceConfigEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MemeSourceConfigRepository extends MongoRepository<MemeSourceConfigEntity, String> {

	List<MemeSourceConfigEntity> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
}
