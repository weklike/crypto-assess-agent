package com.cryptoassess.report;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

	static final MediaType DOCX = MediaType
		.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping("/api/assessments/{id}/report.docx")
	public ResponseEntity<byte[]> report(@PathVariable long id) {
		ReportService.Report report = this.reportService.export(id);
		return ResponseEntity.ok()
			.contentType(DOCX)
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(report.fileName()).build().toString())
			.body(report.content());
	}

}
