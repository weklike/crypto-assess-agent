package com.cryptoassess.assessment;

import java.math.BigDecimal;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工复核：只能在 REVIEW 状态修改差距项；全部确认后才能 confirm。模型结论必须经测评人员确认才进入评分。
 */
@Service
public class ReviewService {

	private final AssessmentService assessmentService;

	private final AssessFindingMapper findingMapper;

	private final ScoringService scoringService;

	private final AssessObjectMapper objectMapper;

	private final JudgmentService judgmentService;

	public ReviewService(AssessmentService assessmentService, AssessFindingMapper findingMapper,
			ScoringService scoringService, AssessObjectMapper objectMapper, JudgmentService judgmentService) {
		this.assessmentService = assessmentService;
		this.findingMapper = findingMapper;
		this.scoringService = scoringService;
		this.objectMapper = objectMapper;
		this.judgmentService = judgmentService;
	}

	@Transactional
	public AssessFinding review(long projectId, long findingId, AssessmentRequests.ReviewFinding request) {
		AssessProject project = this.assessmentService.project(projectId);
		AssessFinding finding = this.findingMapper.findById(findingId);
		if (finding == null || finding.projectId() != projectId) {
			throw AppException.notFound("finding " + findingId + " not found in assessment " + projectId);
		}
		if (project.status() != AssessmentStatus.REVIEW) {
			throw new AppException(ErrorType.STATE_CONFLICT,
					"findings can only be reviewed in status REVIEW, current " + project.status());
		}
		if (request.judgment() != null && !Judgment.VALUES.contains(request.judgment())) {
			throw AppException.invalid("judgment must be one of " + Judgment.VALUES);
		}
		boolean technical = this.judgmentService.technical(this.objectMapper.findById(finding.objectId()).layer());
		Dimensions dims = new Dimensions(finding.judgment(), finding.dimD(), finding.dimA(), finding.dimK(),
				finding.ra(), finding.rk());
		Dimensions updated = technical ? technicalReview(dims, request) : managementReview(dims, request);
		String source = updated.equals(dims) ? finding.source() : AssessFinding.SOURCE_REVIEWER;
		String note = (request.note() != null) ? request.note() : finding.reviewerNote();
		boolean reviewed = (request.reviewed() != null) ? request.reviewed() : finding.reviewed();
		this.findingMapper.review(findingId, updated.judgment(), note, reviewed, source, updated.d(), updated.a(),
				updated.k(), updated.ra(), updated.rk());
		return this.findingMapper.findById(findingId);
	}

	private record Dimensions(String judgment, Boolean d, Boolean a, Boolean k, BigDecimal ra, BigDecimal rk) {
	}

	/**
	 * 技术层面：判定由 D/A/K 推出（量化评估规则表 1）。给了维度就按维度改；只给判定时，
	 * 符合 = 三个维度都满足，不符合 = D 不满足，不适用；“部分符合”说不清是哪一维不满足，必须给出 D/A/K。
	 */
	private static Dimensions technicalReview(Dimensions current, AssessmentRequests.ReviewFinding request) {
		if (request.changesDimensions()) {
			Boolean d = (request.d() != null) ? request.d() : current.d();
			if (d == null) {
				throw AppException.invalid("D (密码使用有效性) is required for technical findings");
			}
			if (!d) {
				return new Dimensions("不符合", false, null, null, null, null);
			}
			Boolean a = (request.a() != null) ? request.a() : current.a();
			Boolean k = (request.k() != null) ? request.k() : current.k();
			if (a == null || k == null) {
				throw AppException.invalid("A and K are required when D is satisfied");
			}
			BigDecimal ra = a ? null : ((request.ra() != null) ? request.ra() : current.ra());
			BigDecimal rk = k ? null : ((request.rk() != null) ? request.rk() : current.rk());
			return new Dimensions(Judgment.fromDimensions(true, a, k), true, a, k, ra, rk);
		}
		if (request.judgment() == null) {
			return current;
		}
		return switch (request.judgment()) {
			case "符合" -> new Dimensions("符合", true, true, true, null, null);
			case "不符合" -> new Dimensions("不符合", false, null, null, null, null);
			case "不适用" -> new Dimensions("不适用", null, null, null, null, null);
			default -> throw AppException.invalid("技术层面的“部分符合”请给出 D/A/K（以及对应的 Ra、Rk）");
		};
	}

	private static Dimensions managementReview(Dimensions current, AssessmentRequests.ReviewFinding request) {
		if (request.changesDimensions()) {
			throw AppException.invalid("management findings are judged directly; D/A/K do not apply");
		}
		return (request.judgment() == null) ? current
				: new Dimensions(request.judgment(), null, null, null, null, null);
	}

	@Transactional
	public AssessProject confirm(long projectId) {
		AssessProject project = this.assessmentService.project(projectId);
		AssessmentWorkflow.next(project.status(), WorkflowEvent.CONFIRM);
		int unreviewed = this.findingMapper.countUnreviewed(projectId);
		if (unreviewed > 0) {
			throw new AppException(ErrorType.STATE_CONFLICT, unreviewed + " finding(s) are not reviewed yet");
		}
		this.assessmentService.transition(project, WorkflowEvent.CONFIRM, null);
		// 确认后立即按规则评分：评分是纯计算，与确认放在同一事务里，要么都成功要么都回滚
		this.scoringService.score(projectId);
		return this.assessmentService.project(projectId);
	}

}
