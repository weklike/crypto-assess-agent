package com.cryptoassess.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cryptoassess.assessment.AssessFinding;
import com.cryptoassess.assessment.AssessObject;
import com.cryptoassess.assessment.AssessProject;
import com.cryptoassess.assessment.AssessScore;
import com.cryptoassess.assessment.AssessmentDetail;
import com.cryptoassess.assessment.AssessmentService;
import com.cryptoassess.assessment.AssessmentWorkflow;
import com.cryptoassess.assessment.WorkflowEvent;
import com.cryptoassess.assessment.AssessObjectMapper;
import com.cryptoassess.common.llm.LlmCallMapper;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.rules.AlgorithmRuleTable;
import com.cryptoassess.rules.OfficialScoring;
import com.cryptoassess.rules.OfficialScoringRules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从数据库组装报告内容并生成 .docx，状态 SCORED → REPORTED。整改建议在判定步骤已落库，导出时不调用模型，
 * 同一份数据导出多次结果一致。
 */
@Service
public class ReportService {

	public record Report(String fileName, byte[] content) {
	}

	private final AssessmentService assessmentService;

	private final AssessObjectMapper objectMapper;

	private final KbClauseMapper clauseMapper;

	private final LlmCallMapper llmCallMapper;

	private final AlgorithmRuleTable ruleTable;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final OfficialScoringRules scoringRules;

	private final ReportGenerator generator;

	public ReportService(AssessmentService assessmentService, AssessObjectMapper objectMapper,
			KbClauseMapper clauseMapper, LlmCallMapper llmCallMapper, AlgorithmRuleTable ruleTable,
			OfficialScoringRules scoringRules, ReportGenerator generator) {
		this.assessmentService = assessmentService;
		this.objectMapper = objectMapper;
		this.clauseMapper = clauseMapper;
		this.llmCallMapper = llmCallMapper;
		this.ruleTable = ruleTable;
		this.scoringRules = scoringRules;
		this.generator = generator;
	}

	@Transactional
	public Report export(long projectId) {
		AssessProject project = this.assessmentService.project(projectId);
		AssessmentWorkflow.next(project.status(), WorkflowEvent.GENERATE_REPORT);
		byte[] content = this.generator.generate(data(projectId));
		this.assessmentService.transition(project, WorkflowEvent.GENERATE_REPORT, null);
		return new Report("assessment-" + projectId + "-draft.docx", content);
	}

	ReportData data(long projectId) {
		AssessmentDetail detail = this.assessmentService.detail(projectId);
		AssessProject project = detail.project();
		Map<Long, AssessObject> objects = this.objectMapper.findByProject(projectId)
			.stream()
			.collect(Collectors.toMap(AssessObject::id, Function.identity()));
		List<AssessFinding> findings = detail.findings();
		Map<String, KbClause> clauses = findings.isEmpty() ? Map.of()
				: this.clauseMapper.findByRefs(findings.stream().map(AssessFinding::clauseRef).distinct().toList())
					.stream()
					.collect(Collectors.toMap(KbClause::clauseRef, Function.identity()));

		BigDecimal total = null;
		String totalNote = null;
		String scoringVersion = this.scoringRules.version();
		List<ReportData.LayerScore> layers = new ArrayList<>();
		for (AssessScore score : detail.scores()) {
			scoringVersion = score.ruleVersion();
			if (score.scope().equals("total")) {
				total = score.score();
				totalNote = note(score.detailJson());
			}
			else if (score.scope().equals("layer")) {
				layers.add(new ReportData.LayerScore(score.scopeKey(), score.score(),
						this.scoringRules.layers().getOrDefault(score.scopeKey(), BigDecimal.ZERO)));
			}
		}
		List<ReportData.Gap> gaps = new ArrayList<>();
		for (AssessFinding f : findings) {
			if (!"不符合".equals(f.judgment()) && !"部分符合".equals(f.judgment())) {
				continue;
			}
			AssessObject object = objects.get(f.objectId());
			KbClause clause = clauses.get(f.clauseRef());
			gaps.add(new ReportData.Gap(object.name(), object.layer(), f.clauseRef(),
					(clause == null) ? null : clause.title(), f.judgment(),
					(f.dimD() == null) ? null : OfficialScoring.describe(f.dimD(), f.dimA(), f.dimK(), f.ra(), f.rk()),
					f.evidence(), f.remediation(),
					f.reviewerNote()));
		}
		List<Long> callIds = findings.stream().map(AssessFinding::llmCallId).filter(Objects::nonNull).distinct().toList();
		TreeSet<String> models = new TreeSet<>();
		TreeSet<String> prompts = new TreeSet<>();
		if (!callIds.isEmpty()) {
			for (Map<String, Object> row : this.llmCallMapper.modelsAndPrompts(callIds)) {
				models.add(String.valueOf(row.get("model")));
				if (row.get("promptVersion") != null) {
					prompts.add(String.valueOf(row.get("promptVersion")));
				}
			}
		}
		return new ReportData(project.systemName(), project.name(), project.level(), Instant.now(), total, totalNote, layers, gaps,
				findings.size(), List.copyOf(models), List.copyOf(prompts), this.ruleTable.ruleVersion(),
				this.ruleTable.reviewed(), scoringVersion, this.scoringRules.reviewed());
	}

	private static String note(String detailJson) {
		if (detailJson == null) {
			return null;
		}
		JsonNode note = JSON.readTree(detailJson).get("note");
		return (note == null || note.isNull()) ? null : note.asString();
	}

}
