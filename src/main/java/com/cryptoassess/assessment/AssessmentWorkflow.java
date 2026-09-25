package com.cryptoassess.assessment;

import java.util.EnumMap;
import java.util.Map;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;

/**
 * 评估工作流状态机：一张（状态, 事件）→ 新状态 的转换表，表外的组合一律 409。
 * 只做转换和校验，不做持久化；持久化由服务层用乐观锁完成。
 */
public final class AssessmentWorkflow {

	private static final Map<AssessmentStatus, Map<WorkflowEvent, AssessmentStatus>> TRANSITIONS = new EnumMap<>(
			AssessmentStatus.class);

	static {
		allow(AssessmentStatus.DRAFT, WorkflowEvent.ADD_OBJECT, AssessmentStatus.READY);
		allow(AssessmentStatus.READY, WorkflowEvent.ADD_OBJECT, AssessmentStatus.READY);
		allow(AssessmentStatus.READY, WorkflowEvent.START_ANALYSIS, AssessmentStatus.ANALYZING);
		allow(AssessmentStatus.ANALYZING, WorkflowEvent.ANALYSIS_SUCCEEDED, AssessmentStatus.REVIEW);
		allow(AssessmentStatus.ANALYZING, WorkflowEvent.ANALYSIS_FAILED, AssessmentStatus.FAILED);
		allow(AssessmentStatus.FAILED, WorkflowEvent.START_ANALYSIS, AssessmentStatus.ANALYZING);
		allow(AssessmentStatus.FAILED, WorkflowEvent.ADD_OBJECT, AssessmentStatus.FAILED);
		allow(AssessmentStatus.REVIEW, WorkflowEvent.ADD_OBJECT, AssessmentStatus.REVIEW);
		allow(AssessmentStatus.REVIEW, WorkflowEvent.START_ANALYSIS, AssessmentStatus.ANALYZING);
		allow(AssessmentStatus.REVIEW, WorkflowEvent.CONFIRM, AssessmentStatus.CONFIRMED);
		allow(AssessmentStatus.CONFIRMED, WorkflowEvent.SCORE, AssessmentStatus.SCORED);
		allow(AssessmentStatus.SCORED, WorkflowEvent.GENERATE_REPORT, AssessmentStatus.REPORTED);
		allow(AssessmentStatus.REPORTED, WorkflowEvent.GENERATE_REPORT, AssessmentStatus.REPORTED);
	}

	private AssessmentWorkflow() {
	}

	private static void allow(AssessmentStatus from, WorkflowEvent event, AssessmentStatus to) {
		TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(WorkflowEvent.class)).put(event, to);
	}

	public static boolean allows(AssessmentStatus from, WorkflowEvent event) {
		return TRANSITIONS.getOrDefault(from, Map.of()).containsKey(event);
	}

	public static AssessmentStatus next(AssessmentStatus from, WorkflowEvent event) {
		AssessmentStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(event);
		if (to == null) {
			throw new AppException(ErrorType.STATE_CONFLICT,
					"event " + event.name() + " is not allowed in status " + from.name(),
					Map.of("status", from.name(), "event", event.name()), null);
		}
		return to;
	}

}
