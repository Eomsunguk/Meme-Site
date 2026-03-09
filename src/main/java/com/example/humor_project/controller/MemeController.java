package com.example.humor_project.controller;

import com.example.humor_project.service.MemeCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;

@Controller
public class MemeController {

	private final MemeCatalogService memeCatalogService;

	public MemeController(MemeCatalogService memeCatalogService) {
		this.memeCatalogService = memeCatalogService;
	}

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("categories", memeCatalogService.getTrendingCategories());
		model.addAttribute("lastUpdatedText", memeCatalogService.getLastUpdatedDate().format(DateTimeFormatter.ISO_DATE));
		model.addAttribute("nextUpdatedText", memeCatalogService.getNextUpdateDate().format(DateTimeFormatter.ISO_DATE));
		return "index";
	}
}
