package com.cryptoassess.retrieval;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

	private final HybridSearchService searchService;

	public SearchController(HybridSearchService searchService) {
		this.searchService = searchService;
	}

	@PostMapping("/api/search")
	public SearchResponse search(@Valid @RequestBody SearchRequest request) {
		return this.searchService.search(request);
	}

}
