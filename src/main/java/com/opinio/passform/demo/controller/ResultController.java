package com.opinio.passform.demo.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.opinio.passform.demo.service.AnalysisResponse;
import com.opinio.passform.demo.service.AnalysisService;
import com.opinio.passform.demo.service.ResultService;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/forms/{formId}")
public class ResultController {

	private final ResultService results;

	private final AnalysisService analysis;

	public ResultController(ResultService results, AnalysisService analysis) {
		this.results = results;
		this.analysis = analysis;
	}

	public record AnalyzeRequest(String query, JsonNode context) {
	}

	@GetMapping("/results")
	public ResultService.ResultsView results(@PathVariable String formId, Authentication authentication) {
		return results.results(formId, authentication.getName());
	}

	@PostMapping("/analyze")
	public AnalysisResponse analyze(@PathVariable String formId, @RequestBody AnalyzeRequest request,
			Authentication authentication) {
		return analysis.analyze(formId, authentication.getName(), request.query(), request.context());
	}

	@GetMapping("/responses/{responseId}")
	public ResultService.OwnerResponseView response(@PathVariable String formId, @PathVariable String responseId,
			Authentication authentication) {
		return results.response(formId, authentication.getName(), responseId);
	}

}
