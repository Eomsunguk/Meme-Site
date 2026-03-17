package com.example.humor_project.controller;

import com.example.humor_project.service.MemeCatalogService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class StoredMediaController {

	private final MemeCatalogService memeCatalogService;

	public StoredMediaController(MemeCatalogService memeCatalogService) {
		this.memeCatalogService = memeCatalogService;
	}

	@GetMapping("/media/{fileName:.+}")
	public ResponseEntity<Resource> media(@PathVariable String fileName) throws IOException {
		Path root = memeCatalogService.getMediaStorageRoot();
		Path file = root.resolve(fileName).normalize();
		if (!file.startsWith(root) || !Files.exists(file) || !Files.isRegularFile(file)) {
			return ResponseEntity.notFound().build();
		}

		Resource resource = new UrlResource(file.toUri());
		String contentType = Files.probeContentType(file);
		if (contentType == null) {
			contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
		}
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.body(resource);
	}
}
