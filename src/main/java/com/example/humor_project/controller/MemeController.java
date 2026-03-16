package com.example.humor_project.controller;

import com.example.humor_project.service.MemeCatalogService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Controller
public class MemeController {
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	private final MemeCatalogService memeCatalogService;

	public MemeController(MemeCatalogService memeCatalogService) {
		this.memeCatalogService = memeCatalogService;
	}

	@GetMapping("/")
	public String index(Model model) {
		model.addAttribute("categories", memeCatalogService.getTrendingCategories());
		model.addAttribute("archives", memeCatalogService.getArchiveMonths());
		model.addAttribute("lastUpdatedText", memeCatalogService.getLastUpdatedDate().format(DateTimeFormatter.ISO_DATE));
		model.addAttribute("nextUpdatedText", memeCatalogService.getNextUpdateDate().format(DateTimeFormatter.ISO_DATE));
		model.addAttribute("currentMonthLabel", memeCatalogService.getLastUpdatedDate().format(MONTH_FORMATTER));
		return "index";
	}
}
