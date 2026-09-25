package com.cryptoassess.assessment;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

	private final AssessmentService assessmentService;

	private final AnalysisRunner analysisRunner;

	private final ReviewService reviewService;

	public AssessmentController(AssessmentService assessmentService, AnalysisRunner analysisRunner,
			ReviewService reviewService) {
		this.assessmentService = assessmentService;
		this.analysisRunner = analysisRunner;
		this.reviewService = reviewService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AssessProject create(@Valid @RequestBody AssessmentRequests.CreateAssessment request) {
		return this.assessmentService.create(request);
	}

	@GetMapping
	public List<AssessProject> list() {
		return this.assessmentService.list();
	}

	@GetMapping("/{id}")
	public AssessmentDetail detail(@PathVariable long id) {
		return this.assessmentService.detail(id);
	}

	@PostMapping("/{id}/objects")
	@ResponseStatus(HttpStatus.CREATED)
	public AssessmentDetail.ObjectView addObject(@PathVariable long id,
			@Valid @RequestBody AssessmentRequests.AddObject request) {
		return this.assessmentService.addObject(id, request);
	}

	/** 启动分析或从失败步骤继续；立即返回 202，前端轮询 GET /api/assessments/{id}。 */
	@PostMapping("/{id}/analyze")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public AssessProject analyze(@PathVariable long id) {
		this.analysisRunner.startAnalysis(id);
		return this.assessmentService.project(id);
	}

	@PatchMapping("/{id}/findings/{fid}")
	public AssessFinding review(@PathVariable long id, @PathVariable long fid,
			@Valid @RequestBody AssessmentRequests.ReviewFinding request) {
		return this.reviewService.review(id, fid, request);
	}

	@PostMapping("/{id}/confirm")
	public AssessProject confirm(@PathVariable long id) {
		return this.reviewService.confirm(id);
	}

}
