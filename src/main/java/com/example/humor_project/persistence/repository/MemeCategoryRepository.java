package com.example.humor_project.persistence.repository;

import com.example.humor_project.persistence.entity.MemeCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemeCategoryRepository extends JpaRepository<MemeCategoryEntity, Long> {

	List<MemeCategoryEntity> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
}
