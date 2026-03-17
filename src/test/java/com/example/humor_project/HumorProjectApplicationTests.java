package com.example.humor_project;

import com.example.humor_project.persistence.repository.MemeCategoryRepository;
import com.example.humor_project.persistence.repository.MemeSourceConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HumorProjectApplicationTests {

	@Autowired
	private MemeCategoryRepository categoryRepository;

	@Autowired
	private MemeSourceConfigRepository sourceConfigRepository;

	@Test
	void contextLoads() {
	}

	@Test
	void seedsDefaultCategories() {
		assertThat(categoryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc())
				.extracting("categoryKey")
				.contains("gaming", "work", "kpop", "sports");
	}

	@Test
	void seedsDefaultRedditSources() {
		assertThat(sourceConfigRepository.findAllByActiveTrueOrderByCategory_DisplayOrderAscDisplayOrderAscIdAsc())
				.isNotEmpty();
	}

}
