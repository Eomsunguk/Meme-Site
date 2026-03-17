package com.example.humor_project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HumorProjectApplicationTests {

	@Autowired
	private HumorProjectApplication application;

	@Test
	void contextLoads() {
		assertThat(application).isNotNull();
	}

}
