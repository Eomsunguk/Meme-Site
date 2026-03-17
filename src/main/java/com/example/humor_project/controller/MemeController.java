package com.example.humor_project.controller;

import com.example.humor_project.model.MemeArchiveMonth;
import com.example.humor_project.model.MemeCategory;
import com.example.humor_project.service.MemeCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Controller
public class MemeController {
	private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
	private static final Logger log = LoggerFactory.getLogger(MemeController.class);

	private final MemeCatalogService memeCatalogService;

	public MemeController(MemeCatalogService memeCatalogService) {
		this.memeCatalogService = memeCatalogService;
	}

	@GetMapping("/")
	public String index(Model model) {
		try {
			LocalDate lastUpdatedDate = memeCatalogService.getLastUpdatedDate();
			LocalDate nextUpdatedDate = memeCatalogService.getNextUpdateDate();
			List<MemeCategory> categories = memeCatalogService.getTrendingCategories();
			List<MemeArchiveMonth> archives = memeCatalogService.getArchiveMonths();
			model.addAttribute("categories", categories);
			model.addAttribute("archives", archives);
			model.addAttribute("lastUpdatedText", lastUpdatedDate.format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("nextUpdatedText", nextUpdatedDate.format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("currentMonthLabel", lastUpdatedDate.format(MONTH_FORMATTER));
		} catch (Exception exception) {
			LocalDate today = LocalDate.now();
			log.error("Failed to render meme index page", exception);
			model.addAttribute("categories", List.of());
			model.addAttribute("archives", List.of());
			model.addAttribute("lastUpdatedText", today.format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("nextUpdatedText", today.plusWeeks(1).format(DateTimeFormatter.ISO_DATE));
			model.addAttribute("currentMonthLabel", today.format(MONTH_FORMATTER));
		}
		return "index";
	}

	@GetMapping("/health")
	@ResponseBody
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
