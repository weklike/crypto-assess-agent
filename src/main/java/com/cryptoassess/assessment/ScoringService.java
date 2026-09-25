package com.cryptoassess.assessment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import com.cryptoassess.rules.OfficialScoreResult;
import com.cryptoassess.rules.OfficialScoringCalculator;
import com.cryptoassess.rules.OfficialScoringRules;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 用已确认的差距项按量化评估规则计算得分并写 assess_score（带 rule_version），状态 CONFIRMED → SCORED。
 * 评分所需信息不完整（条款未映射到测评单元、缺 D/A/K 或 Ra/Rk）时返回 409，确认一并回滚，项目留在 REVIEW。
 */
@Service
public class ScoringService {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AssessmentService assessmentService;

	private final AssessObjectMapper objectMapper;

	private final AssessFindingMapper findingMapper;

	private final AssessScoreMapper scoreMapper;

	private final KbClauseMapper clauseMapper;

	private final OfficialScoringRules rules;

	public ScoringService(AssessmentService assessmentService, AssessObjectMapper objectMapper,
			AssessFindingMapper findingMapper, AssessScoreMapper scoreMapper, KbClauseMapper clauseMapper,
			OfficialScoringRules rules) {
		this.assessmentService = assessmentService;
		this.objectMapper = objectMapper;
		this.findingMapper = findingMapper;
		this.scoreMapper = scoreMapper;
		this.clauseMapper = clauseMapper;
		this.rules = rules;
	}

	public OfficialScoreResult calculate(long projectId) {
		AssessProject project = this.assessmentService.project(projectId);
		Map<Long, AssessObject> objects = this.objectMapper.findByProject(projectId)
			.stream()
			.collect(Collectors.toMap(AssessObject::id, Function.identity()));
		List<AssessFinding> findings = this.findingMapper.findByProject(projectId);
		Map<String, String> titles = findings.isEmpty() ? Map.of()
				: this.clauseMapper.findByRefs(findings.stream().map(AssessFinding::clauseRef).distinct().toList())
					.stream()
					.collect(Collectors.toMap(KbClause::clauseRef, KbClause::title, (x, y) -> x));
		List<OfficialScoringCalculator.Item> items = new ArrayList<>();
		for (AssessFinding f : findings) {
			AssessObject object = objects.get(f.objectId());
			String key = String.valueOf(object.id());
			String title = titles.get(f.clauseRef());
			if ("不适用".equals(f.judgment())) {
				items.add(OfficialScoringCalculator.Item.notApplicable(key, object.name(), object.layer(), f.clauseRef(),
						title));
			}
			else if (this.rules.groupOf(object.layer()).map(OfficialScoringRules.Group::dak).orElse(false)) {
				items.add(OfficialScoringCalculator.Item.technical(key, object.name(), object.layer(), f.clauseRef(), title,
						f.dimD(), f.dimA(), f.dimK(), f.ra(), f.rk()));
			}
			else {
				items.add(OfficialScoringCalculator.Item.management(key, object.name(), object.layer(), f.clauseRef(),
						title, f.judgment()));
			}
		}
		try {
			return OfficialScoringCalculator.calculate(this.rules, project.level(), items);
		}
		catch (IllegalArgumentException ex) {
			throw new AppException(ErrorType.STATE_CONFLICT, "评分所需信息不完整：" + ex.getMessage());
		}
	}

	@Transactional
	public OfficialScoreResult score(long projectId) {
		AssessProject project = this.assessmentService.project(projectId);
		AssessmentWorkflow.next(project.status(), WorkflowEvent.SCORE);
		OfficialScoreResult result = calculate(projectId);
		String version = result.ruleVersion();
		this.scoreMapper.deleteByProject(projectId);
		Map<String, Object> totalDetail = new LinkedHashMap<>();
		totalDetail.put("note", result.totalNote());
		totalDetail.put("level", result.level());
		totalDetail.put("reviewedRules", this.rules.reviewed());
		totalDetail.put("detail", result.detail());
		insert(projectId, "total", "total", result.total(), totalDetail, version);
		result.groups().forEach((name, score) -> insert(projectId, "group", name, score, Map.of(), version));
		result.layers().forEach((layer, score) -> insert(projectId, "layer", layer, score,
				Map.of("weight", this.rules.layerWeight(layer), "applicable", score != null), version));
		result.units().forEach((unit, score) -> insert(projectId, "unit", unit, score, Map.of(), version));
		Map<Long, AssessObject> objects = new LinkedHashMap<>();
		this.objectMapper.findByProject(projectId).forEach(o -> objects.put(o.id(), o));
		result.objects().forEach((key, score) -> {
			AssessObject object = objects.get(Long.valueOf(key));
			insert(projectId, "object", key, score, Map.of("name", object.name(), "layer", object.layer()), version);
		});
		this.assessmentService.transition(project, WorkflowEvent.SCORE, null);
		return result;
	}

	private void insert(long projectId, String scope, String key, BigDecimal score, Map<String, Object> detail,
			String ruleVersion) {
		this.scoreMapper.insert(new AssessScore(null, projectId, scope, key, score, JSON.writeValueAsString(detail),
				ruleVersion, null));
	}

}
