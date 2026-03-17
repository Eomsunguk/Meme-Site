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
import java.net.URISyntaxException;

@Configuration
public class MongoConfiguration {

	private static final Logger log = LoggerFactory.getLogger(MongoConfiguration.class);
	private static final String DEFAULT_DATABASE = "humor_project";

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
		String normalizedMongoUri = ensureDatabaseName(mongoUri);
		URI uri = URI.create(normalizedMongoUri);
		log.info("Creating MongoDatabaseFactory with host: {} and database: {}", uri.getHost(), extractDatabaseName(uri));
		return new SimpleMongoClientDatabaseFactory(normalizedMongoUri);
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

	private static String ensureDatabaseName(String mongoUri) {
		URI uri = URI.create(mongoUri);
		String path = uri.getPath();
		if (path != null && !path.isBlank() && !"/".equals(path)) {
			return mongoUri;
		}
		try {
			return new URI(
					uri.getScheme(),
					uri.getUserInfo(),
					uri.getHost(),
					uri.getPort(),
					"/" + DEFAULT_DATABASE,
					uri.getQuery(),
					uri.getFragment()
			).toString();
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("Could not normalize MongoDB URI.", exception);
		}
	}

	private static String extractDatabaseName(URI uri) {
		String path = uri.getPath();
		if (path == null || path.isBlank() || "/".equals(path)) {
			return DEFAULT_DATABASE;
		}
		return path.startsWith("/") ? path.substring(1) : path;
	}
}
