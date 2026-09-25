package com.cryptoassess.assessment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import com.cryptoassess.knowledge.KbClause;
import com.cryptoassess.knowledge.KbClauseMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 评估工作流执行器。每个测评对象依次执行 要求匹配 → 规则检查 → 结构化判定（每条指标一步）；
 * 每步开始写 RUNNING、结束写 SUCCEEDED（带结果）或 FAILED。再次分析时跳过已成功的步骤，
 * 因此失败后重跑、进程重启后续跑都只执行剩余步骤。模型调用在事务之外；步骤结果和差距项在同一个短事务里落库。
 */
@Service
public class AnalysisRunner {

	private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

	static final String MATCH = "MATCH";

	static final String RULE_CHECK = "RULE_CHECK";

	static final String JUDGE = "JUDGE";

	static final String NO_KEY = "-";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AssessmentService assessmentService;

	private final AssessProjectMapper projectMapper;

	private final AssessObjectMapper objectMapper;

	private final AssessStepMapper stepMapper;

	private final AssessFindingMapper findingMapper;

	private final KbClauseMapper clauseMapper;

	private final RequirementMatcher matcher;

	private final JudgmentService judgmentService;

	private final TransactionTemplate tx;

	private final WorkflowProperties properties;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public AnalysisRunner(AssessmentService assessmentService, AssessProjectMapper projectMapper,
			AssessObjectMapper objectMapper, AssessStepMapper stepMapper, AssessFindingMapper findingMapper,
			KbClauseMapper clauseMapper, RequirementMatcher matcher, JudgmentService judgmentService,
			TransactionTemplate tx, WorkflowProperties properties) {
		this.assessmentService = assessmentService;
		this.projectMapper = projectMapper;
		this.objectMapper = objectMapper;
		this.stepMapper = stepMapper;
		this.findingMapper = findingMapper;
		this.clauseMapper = clauseMapper;
		this.matcher = matcher;
		this.judgmentService = judgmentService;
		this.tx = tx;
		this.properties = properties;
	}

	/** 同步完成状态转换（非法时 409），分析在后台执行。 */
	public void startAnalysis(long projectId) {
		AssessProject project = this.assessmentService.project(projectId);
		this.assessmentService.transition(project, WorkflowEvent.START_ANALYSIS, null);
		this.executor.execute(() -> run(projectId));
	}

	/** 同步执行（评测运行器使用），返回最终状态。 */
	public AssessmentStatus analyzeNow(long projectId) {
		AssessProject project = this.assessmentService.project(projectId);
		this.assessmentService.transition(project, WorkflowEvent.START_ANALYSIS, null);
		run(projectId);
		return this.assessmentService.project(projectId).status();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onReady() {
		if (this.properties.resumeOnStartup()) {
			recoverInterrupted();
		}
	}

	/**
	 * 进程重启后继续被中断的分析：停在 RUNNING 的步骤没有落库结果，先标记为失败，再从头遍历（已成功的步骤会被跳过）。
	 */
	public void recoverInterrupted() {
		for (AssessProject project : this.projectMapper.findAll()) {
			if (project.status() == AssessmentStatus.ANALYZING) {
				log.info("Resuming interrupted analysis of assessment {}", project.id());
				this.stepMapper.failRunning(project.id(), "interrupted by restart");
				this.executor.execute(() -> run(project.id()));
			}
		}
	}

	void run(long projectId) {
		try {
			AssessProject project = this.assessmentService.project(projectId);
			for (AssessObject object : this.objectMapper.findByProject(projectId)) {
				analyzeObject(project, object);
			}
			this.assessmentService.transition(this.assessmentService.project(projectId),
					WorkflowEvent.ANALYSIS_SUCCEEDED, null);
			log.info("Assessment {} analysis finished", projectId);
		}
		catch (StepFailure failure) {
			markFailed(projectId, failure.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Assessment {} analysis crashed", projectId, ex);
			markFailed(projectId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
		}
	}

	private void markFailed(long projectId, String error) {
		log.warn("Assessment {} analysis failed: {}", projectId, error);
		this.assessmentService.transition(this.assessmentService.project(projectId), WorkflowEvent.ANALYSIS_FAILED,
				truncate(error, 1000));
	}

	private void analyzeObject(AssessProject project, AssessObject object) {
		RequirementMatcher.Match match = step(project, object, MATCH, NO_KEY,
				() -> this.matcher.match(project.level(), object), RequirementMatcher.Match.class);
		step(project, object, RULE_CHECK, NO_KEY,
				() -> Map.of("checks", this.judgmentService.ruleChecks(object.measures())), Map.class);
		if (match.targets().isEmpty()) {
			return;
		}
		List<String> refs = new ArrayList<>(match.targets());
		refs.addAll(match.supplements());
		Map<String, KbClause> clauses = new java.util.HashMap<>();
		this.clauseMapper.findByRefs(refs).forEach(c -> clauses.put(c.clauseRef(), c));
		for (String target : match.targets()) {
			AssessStep done = this.stepMapper.find(project.id(), object.id(), JUDGE, target);
			if (done != null && AssessStep.SUCCEEDED.equals(done.status())) {
				continue;
			}
			List<JudgeInput.Clause> context = new ArrayList<>();
			context.add(toClause(clauses.get(target)));
			match.supplements().stream().filter(clauses::containsKey).map(r -> toClause(clauses.get(r))).forEach(context::add);
			JudgeInput input = new JudgeInput(project.level(), object.name(), object.layer(), object.description(),
					object.measures(), context);
			this.stepMapper.start(project.id(), object.id(), JUDGE, target);
			Judgment judgment;
			try {
				judgment = this.judgmentService.judge(input);
			}
			catch (RuntimeException ex) {
				this.stepMapper.fail(project.id(), object.id(), JUDGE, target, truncate(ex.getMessage(), 1000));
				throw new StepFailure(JUDGE + " " + object.name() + " " + target + ": " + ex.getMessage());
			}
			this.tx.executeWithoutResult(status -> {
				this.findingMapper.upsert(toFinding(project.id(), object.id(), target, judgment));
				this.stepMapper.succeed(project.id(), object.id(), JUDGE, target, JSON.writeValueAsString(judgment));
			});
		}
	}

	/** 执行一个无子项的步骤；已成功则直接复用落库的结果。 */
	private <T> T step(AssessProject project, AssessObject object, String step, String key, Supplier<T> action,
			Class<T> type) {
		AssessStep existing = this.stepMapper.find(project.id(), object.id(), step, key);
		if (existing != null && AssessStep.SUCCEEDED.equals(existing.status()) && existing.outputJson() != null) {
			return JSON.readValue(existing.outputJson(), type);
		}
		this.stepMapper.start(project.id(), object.id(), step, key);
		T result;
		try {
			result = action.get();
		}
		catch (RuntimeException ex) {
			this.stepMapper.fail(project.id(), object.id(), step, key, truncate(ex.getMessage(), 1000));
			throw new StepFailure(step + " " + object.name() + ": " + ex.getMessage());
		}
		this.stepMapper.succeed(project.id(), object.id(), step, key, JSON.writeValueAsString(result));
		return result;
	}

	static AssessFinding toFinding(long projectId, long objectId, String clauseRef, Judgment j) {
		Map<String, Object> ruleHits = new java.util.LinkedHashMap<>();
		ruleHits.put("checks", j.ruleChecks());
		ruleHits.put("relevantAlgorithms", j.relevantAlgorithms());
		ruleHits.put("ruleOverride", j.ruleOverride());
		ruleHits.put("pendingParameters", j.pendingParameters());
		ruleHits.put("citedClauses", j.clauseRefs());
		return new AssessFinding(null, projectId, objectId, clauseRef, j.judgment(), j.evidence(),
				JSON.writeValueAsString(ruleHits), j.rationale(), j.remediation(), JSON.writeValueAsString(j.missingInfo()),
				j.ruleOverride() ? AssessFinding.SOURCE_RULE : AssessFinding.SOURCE_MODEL, j.llmCallId(), false, null,
				null, null, null, j.d(), j.a(), j.k(), j.ra(), j.rk(), j.moduleLevel());
	}

	private static JudgeInput.Clause toClause(KbClause clause) {
		return new JudgeInput.Clause(clause.clauseRef(), clause.clauseNo() + " " + clause.title(), clause.body());
	}

	static List<String> readList(String json) {
		return (json == null) ? List.of() : JSON.readValue(json, new TypeReference<List<String>>() {
		});
	}

	private static String truncate(String text, int max) {
		if (text == null) {
			return null;
		}
		return (text.length() <= max) ? text : text.substring(0, max);
	}

	@PreDestroy
	void shutdown() {
		this.executor.shutdownNow();
	}

	/** 某一步失败：已记入 assess_step，项目进入 FAILED。 */
	static final class StepFailure extends RuntimeException {

		StepFailure(String message) {
			super(message);
		}

	}

}
