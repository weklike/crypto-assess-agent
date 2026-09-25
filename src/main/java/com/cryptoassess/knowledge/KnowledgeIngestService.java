package com.cryptoassess.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.knowledge.format.ClauseRecord;
import com.cryptoassess.knowledge.format.NormalizedStandardParser;
import com.cryptoassess.knowledge.format.ParsedStandard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 规范化标准入库。幂等键是 (doc_code, normalized_sha256)：同一内容重复导入直接返回已有文档；
 * 内容变化时新文档替换同一标准号下的旧版本，旧文档标记为 SUPERSEDED、旧条款删除。
 */
@Service
public class KnowledgeIngestService {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

	private static final int INSERT_BATCH_SIZE = 200;

	private final KbDocumentMapper documentMapper;

	private final KbClauseMapper clauseMapper;

	private final TransactionTemplate transactionTemplate;

	private final Path normalizedDir;

	private final NormalizedStandardParser parser = new NormalizedStandardParser();

	public KnowledgeIngestService(KbDocumentMapper documentMapper, KbClauseMapper clauseMapper,
			TransactionTemplate transactionTemplate, KnowledgeProperties properties) {
		this.documentMapper = documentMapper;
		this.clauseMapper = clauseMapper;
		this.transactionTemplate = transactionTemplate;
		this.normalizedDir = Path.of(properties.normalizedDir()).toAbsolutePath().normalize();
	}

	public ImportResult importDocument(String fileName) {
		ParsedStandard parsed = this.parser.parse(readLines(resolve(fileName)));
		KbDocument existing = this.documentMapper.findByCodeAndSha(parsed.docCode(), parsed.normalizedSha256());
		if (existing != null) {
			return new ImportResult(existing.id(), existing.docCode(), existing.clauseCount(), false, existing.normalizedSha256());
		}
		try {
			ImportResult result = this.transactionTemplate.execute(status -> persist(parsed));
			log.info("Imported {} with {} clauses", parsed.docCode(), parsed.clauses().size());
			return result;
		}
		catch (DuplicateKeyException ex) {
			// 并发导入同一内容：另一个请求已经写入，按幂等语义返回它
			KbDocument winner = this.documentMapper.findByCodeAndSha(parsed.docCode(), parsed.normalizedSha256());
			if (winner == null) {
				throw ex;
			}
			return new ImportResult(winner.id(), winner.docCode(), winner.clauseCount(), false, winner.normalizedSha256());
		}
	}

	/** 只解析、不入库：异步入库在排队前用它做同步校验并取得内容哈希。 */
	public ParsedStandard parse(String fileName) {
		return this.parser.parse(readLines(resolve(fileName)));
	}

	public List<KbDocument> listDocuments() {
		return this.documentMapper.findAll();
	}

	public record ClausePage(List<KbClause> items, int total, int page, int size) {
	}

	/** 按原文顺序分页浏览条款（检索调试页没有查询语句时的默认列表）。 */
	public ClausePage clausePage(int page, int size, String layer, Integer level) {
		if (page < 1) {
			throw AppException.invalid("page must be >= 1");
		}
		if (size < 1 || size > 50) {
			throw AppException.invalid("size must be 1-50");
		}
		String layerFilter = (layer == null || layer.isBlank()) ? null : layer;
		int total = this.clauseMapper.countPage(layerFilter, level);
		List<KbClause> items = this.clauseMapper.findPage(layerFilter, level, (page - 1) * size, size);
		return new ClausePage(items, total, page, size);
	}

	public Optional<KbClause> findClause(String clauseRef) {
		return Optional.ofNullable(this.clauseMapper.findByRef(clauseRef));
	}

	private ImportResult persist(ParsedStandard parsed) {
		this.clauseMapper.deleteByDocCode(parsed.docCode());
		this.documentMapper.supersedeActive(parsed.docCode());
		this.documentMapper.insert(new KbDocument(null, parsed.docCode(), parsed.title(), parsed.sourceSha256(),
				parsed.normalizedSha256(), NormalizedStandardParser.PARSER_VERSION, KbDocument.ACTIVE,
				parsed.clauses().size(), Instant.now()));
		long documentId = this.documentMapper.findByCodeAndSha(parsed.docCode(), parsed.normalizedSha256()).id();
		List<ClauseRecord> clauses = parsed.clauses();
		for (int from = 0; from < clauses.size(); from += INSERT_BATCH_SIZE) {
			this.clauseMapper.insertBatch(documentId,
					clauses.subList(from, Math.min(from + INSERT_BATCH_SIZE, clauses.size())));
		}
		return new ImportResult(documentId, parsed.docCode(), clauses.size(), true, parsed.normalizedSha256());
	}

	/** 只接受目录下的纯文件名，防止通过 ../ 读取目录外的文件。 */
	private Path resolve(String fileName) {
		Path file = this.normalizedDir.resolve(fileName).normalize();
		if (!file.getParent().equals(this.normalizedDir)) {
			throw AppException.invalid("fileName must be a file directly under the normalized directory");
		}
		return file;
	}

	private static List<String> readLines(Path file) {
		try {
			return Files.readAllLines(file, StandardCharsets.UTF_8);
		}
		catch (NoSuchFileException ex) {
			throw AppException.notFound("normalized file not found: " + file.getFileName());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read " + file.getFileName(), ex);
		}
	}

}
