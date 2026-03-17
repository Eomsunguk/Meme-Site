package com.example.humor_project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URI;

@SpringBootApplication
@EnableScheduling
public class HumorProjectApplication {

	private static final Logger log = LoggerFactory.getLogger(HumorProjectApplication.class);

	public static void main(String[] args) {
		String mongoUri = firstNonBlank(System.getenv("MONGODB_URI"), System.getenv("MONGO_URL"));
		if (mongoUri == null) {
			throw new IllegalStateException("MongoDB URI is missing. Set MONGODB_URI or MONGO_URL.");
		}
		System.setProperty("spring.data.mongodb.uri", mongoUri);
		log.info("Resolved Mongo host before startup: {}", URI.create(mongoUri).getHost());
		SpringApplication.run(HumorProjectApplication.class, args);
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}
}
