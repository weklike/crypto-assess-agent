package com.cryptoassess.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.cryptoassess.common.db.GeneratedKey;
import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import com.cryptoassess.knowledge.format.NormalizedFormat;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评估项目与测评对象的增删改查。状态变更统一走 {@link #transition}：先用状态机校验，再用乐观锁落库。
 */
@Service
public class AssessmentService {

	private final AssessProjectMapper projectMapper;

	private final AssessObjectMapper objectMapper;

	private final AssessFindingMapper findingMapper;

	private final AssessStepMapper stepMapper;

	private final AssessScoreMapper scoreMapper;

	private final Validator validator;

	public AssessmentService(AssessProjectMapper projectMapper, AssessObjectMapper objectMapper,
			AssessFindingMapper findingMapper, AssessStepMapper stepMapper, AssessScoreMapper scoreMapper,
			Validator validator) {
		this.validator = validator;
		this.projectMapper = projectMapper;
		this.objectMapper = objectMapper;
		this.findingMapper = findingMapper;
		this.stepMapper = stepMapper;
		this.scoreMapper = scoreMapper;
	}

	@Transactional
	public AssessProject create(AssessmentRequests.CreateAssessment request) {
		Instant now = Instant.now();
		GeneratedKey key = new GeneratedKey();
		this.projectMapper.insert(new AssessProject(null, request.name().strip(), request.systemName().strip(),
				request.level(), AssessmentStatus.DRAFT, null, 0, now, now), key);
		return this.projectMapper.findById(key.getId());
	}

	public List<AssessProject> list() {
		return this.projectMapper.findAll();
	}

	public AssessProject project(long id) {
		AssessProject project = this.projectMapper.findById(id);
		if (project == null) {
			throw AppException.notFound("assessment " + id + " not found");
		}
		return project;
	}

	public AssessmentDetail detail(long id) {
		AssessProject project = project(id);
		return new AssessmentDetail(project,
				this.objectMapper.findByProject(id).stream().map(AssessmentDetail.ObjectView::of).toList(),
				this.stepMapper.findByProject(id), this.findingMapper.findByProject(id),
				this.scoreMapper.findByProject(id));
	}

	/**
	 * 添加测评对象。状态转换与插入在同一事务里：乐观锁冲突时对象也不会写入。
	 */
	@Transactional
	public AssessmentDetail.ObjectView addObject(long projectId, AssessmentRequests.AddObject request) {
		if (!NormalizedFormat.LAYERS.contains(request.layer())) {
			throw AppException.invalid("layer must be one of " + NormalizedFormat.LAYERS);
		}
		CryptoMeasures measures = CryptoMeasures.parse(request.measures().toString());
		Set<ConstraintViolation<CryptoMeasures>> violations = this.validator.validate(measures);
		if (!violations.isEmpty()) {
			ConstraintViolation<CryptoMeasures> first = violations.iterator().next();
			throw AppException.invalid("measures." + first.getPropertyPath() + " " + first.getMessage());
		}
		AssessProject project = project(projectId);
		transition(project, WorkflowEvent.ADD_OBJECT, null);
		Instant now = Instant.now();
		GeneratedKey key = new GeneratedKey();
		this.objectMapper.insert(new AssessObject(null, projectId, request.layer(), request.name().strip(),
				request.description(), measures.toJson(), now, now), key);
		return AssessmentDetail.ObjectView.of(this.objectMapper.findById(key.getId()));
	}

	/**
	 * 按状态机转换状态并用 version 做乐观锁；并发修改导致更新 0 行时返回 409。
	 */
	@Transactional
	public AssessmentStatus transition(AssessProject project, WorkflowEvent event, String lastError) {
		AssessmentStatus next = AssessmentWorkflow.next(project.status(), event);
		int updated = this.projectMapper.transition(project.id(), project.status(), next, project.version(), lastError);
		if (updated == 0) {
			throw new AppException(ErrorType.STATE_CONFLICT,
					"assessment " + project.id() + " was modified concurrently, reload and retry");
		}
		return next;
	}

}
