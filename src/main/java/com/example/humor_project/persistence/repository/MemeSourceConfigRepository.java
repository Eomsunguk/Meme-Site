package com.example.humor_project.persistence.repository;

import com.example.humor_project.persistence.entity.MemeSourceConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemeSourceConfigRepository extends JpaRepository<MemeSourceConfigEntity, Long> {

	List<MemeSourceConfigEntity> findAllByActiveTrueOrderByCategory_DisplayOrderAscDisplayOrderAscIdAsc();
}
