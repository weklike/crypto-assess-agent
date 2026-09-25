package com.cryptoassess.knowledge;

import java.util.List;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.knowledge.index.ClauseIndexer;
import com.cryptoassess.knowledge.ingest.AsyncIngestService;
import com.cryptoassess.knowledge.ingest.IngestJob;
import org.springframework.beans.factory.ObjectProvider;
import com.cryptoassess.knowledge.index.ReindexResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeController {

	private final KnowledgeIngestService ingestService;

	private final ClauseIndexer clauseIndexer;

	private final ObjectProvider<AsyncIngestService> asyncIngest;

	public KnowledgeController(KnowledgeIngestService ingestService, ClauseIndexer clauseIndexer,
			ObjectProvider<AsyncIngestService> asyncIngest) {
		this.ingestService = ingestService;
		this.clauseIndexer = clauseIndexer;
		this.asyncIngest = asyncIngest;
	}

	/**
	 * 导入规范化标准。INGEST_MODE=kafka 时写 ingest_job 并发消息，返回 202（内容已入库时 200）；否则同步入库。
	 */
	@PostMapping("/documents")
	public ResponseEntity<?> importDocument(@Valid @RequestBody ImportRequest request) {
		AsyncIngestService async = this.asyncIngest.getIfAvailable();
		if (async != null) {
			AsyncIngestService.Submission submission = async.submit(request.fileName());
			return ResponseEntity.status(submission.accepted() ? HttpStatus.ACCEPTED : HttpStatus.OK)
				.body(submission.job());
		}
		ImportResult result = this.ingestService.importDocument(request.fileName());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
	}

	@GetMapping("/ingest-jobs/{id}")
	public IngestJob ingestJob(@PathVariable long id) {
		AsyncIngestService async = this.asyncIngest.getIfAvailable();
		if (async == null) {
			throw AppException.notFound("asynchronous ingestion is not enabled");
		}
		return async.job(id);
	}

	@GetMapping("/documents")
	public List<KbDocument> listDocuments() {
		return this.ingestService.listDocuments();
	}

	@GetMapping("/clauses")
	public KbClause clause(@RequestParam("ref") String ref) {
		return this.ingestService.findClause(ref).orElseThrow(() -> AppException.notFound("clause not found: " + ref));
	}

	@GetMapping("/clauses/page")
	public KnowledgeIngestService.ClausePage clausePage(@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String layer,
			@RequestParam(required = false) Integer level) {
		return this.ingestService.clausePage(page, size, layer, level);
	}

	/** 全量重建索引并切换别名。二期加鉴权，目前只在本机使用。 */
	@PostMapping("/reindex")
	public ReindexResult reindex() {
		return this.clauseIndexer.reindex();
	}

	/**
	 * @param fileName data/normalized/ 下的文件名，不允许带路径
	 */
	public record ImportRequest(
			@NotBlank @Pattern(regexp = "^[^/\\\\]+\\.md$", message = "must be a .md file name without path") String fileName) {
	}

}
