package com.example.humor_project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.net.URI;

@Configuration
public class MongoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(MongoConfiguration.class);

	@Bean
	public MongoDatabaseFactory mongoDatabaseFactory(Environment environment) {
		String mongoUri = firstNonBlank(
				System.getProperty("spring.data.mongodb.uri"),
				environment.getProperty("spring.data.mongodb.uri"),
				environment.getProperty("MONGODB_URI"),
				environment.getProperty("MONGO_URL")
		);
		if (mongoUri == null) {
			throw new IllegalStateException("MongoDB URI is missing. Set MONGODB_URI or MONGO_URL.");
		}
		log.info("Creating MongoDatabaseFactory with host: {}", URI.create(mongoUri).getHost());
		return new SimpleMongoClientDatabaseFactory(mongoUri);
	}

	@Bean
	public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
		return new MongoTemplate(mongoDatabaseFactory);
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
