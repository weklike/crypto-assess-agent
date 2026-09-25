package com.cryptoassess.assessment;

import static com.cryptoassess.assessment.AssessmentStatus.ANALYZING;
import static com.cryptoassess.assessment.AssessmentStatus.CONFIRMED;
import static com.cryptoassess.assessment.AssessmentStatus.DRAFT;
import static com.cryptoassess.assessment.AssessmentStatus.FAILED;
import static com.cryptoassess.assessment.AssessmentStatus.READY;
import static com.cryptoassess.assessment.AssessmentStatus.REPORTED;
import static com.cryptoassess.assessment.AssessmentStatus.REVIEW;
import static com.cryptoassess.assessment.AssessmentStatus.SCORED;
import static com.cryptoassess.assessment.WorkflowEvent.ADD_OBJECT;
import static com.cryptoassess.assessment.WorkflowEvent.ANALYSIS_FAILED;
import static com.cryptoassess.assessment.WorkflowEvent.ANALYSIS_SUCCEEDED;
import static com.cryptoassess.assessment.WorkflowEvent.CONFIRM;
import static com.cryptoassess.assessment.WorkflowEvent.GENERATE_REPORT;
import static com.cryptoassess.assessment.WorkflowEvent.SCORE;
import static com.cryptoassess.assessment.WorkflowEvent.START_ANALYSIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AssessmentWorkflowTest {

	/** 期望的完整转换表（开发计划 §7.4）；表里没有的组合都应当返回 409。 */
	private static final Map<AssessmentStatus, Map<WorkflowEvent, AssessmentStatus>> EXPECTED = new EnumMap<>(
			AssessmentStatus.class);

	static {
		put(DRAFT, ADD_OBJECT, READY);
		put(READY, ADD_OBJECT, READY);
		put(READY, START_ANALYSIS, ANALYZING);
		put(ANALYZING, ANALYSIS_SUCCEEDED, REVIEW);
		put(ANALYZING, ANALYSIS_FAILED, FAILED);
		put(FAILED, START_ANALYSIS, ANALYZING);
		put(FAILED, ADD_OBJECT, FAILED);
		put(REVIEW, ADD_OBJECT, REVIEW);
		put(REVIEW, START_ANALYSIS, ANALYZING);
		put(REVIEW, CONFIRM, CONFIRMED);
		put(CONFIRMED, SCORE, SCORED);
		put(SCORED, GENERATE_REPORT, REPORTED);
		put(REPORTED, GENERATE_REPORT, REPORTED);
	}

	private static void put(AssessmentStatus from, WorkflowEvent event, AssessmentStatus to) {
		EXPECTED.computeIfAbsent(from, k -> new EnumMap<>(WorkflowEvent.class)).put(event, to);
	}

	static Stream<Arguments> allCombinations() {
		return Stream.of(AssessmentStatus.values())
			.flatMap(status -> Stream.of(WorkflowEvent.values()).map(event -> Arguments.of(status, event)));
	}

	@ParameterizedTest(name = "{0} + {1}")
	@MethodSource("allCombinations")
	void transitionTableIsExactlyAsDesigned(AssessmentStatus from, WorkflowEvent event) {
		AssessmentStatus expected = EXPECTED.getOrDefault(from, Map.of()).get(event);
		if (expected != null) {
			assertThat(AssessmentWorkflow.next(from, event)).isEqualTo(expected);
			assertThat(AssessmentWorkflow.allows(from, event)).isTrue();
		}
		else {
			assertThat(AssessmentWorkflow.allows(from, event)).isFalse();
			assertThatThrownBy(() -> AssessmentWorkflow.next(from, event)).isInstanceOf(AppException.class)
				.satisfies(ex -> assertThat(((AppException) ex).type()).isEqualTo(ErrorType.STATE_CONFLICT))
				.hasMessageContaining(from.name())
				.hasMessageContaining(event.name());
		}
	}

}
