package com.cryptoassess.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import com.cryptoassess.common.error.AppException;
import com.cryptoassess.common.error.ErrorType;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class AssessmentServiceTest {

	private final AssessProjectMapper projectMapper = mock(AssessProjectMapper.class);

	private final AssessmentService service = new AssessmentService(projectMapper, mock(AssessObjectMapper.class),
			mock(AssessFindingMapper.class), mock(AssessStepMapper.class), mock(AssessScoreMapper.class),
			Validation.buildDefaultValidatorFactory().getValidator());

	private static AssessProject project(AssessmentStatus status, int version) {
		return new AssessProject(1L, "p", "s", 3, status, null, version, Instant.now(), Instant.now());
	}

	@Test
	void staleVersionIsAConflict() {
		given(projectMapper.transition(anyLong(), any(), any(), anyInt(), any())).willReturn(0);

		assertThatThrownBy(() -> service.transition(project(AssessmentStatus.READY, 3), WorkflowEvent.START_ANALYSIS, null))
			.isInstanceOf(AppException.class)
			.satisfies(ex -> assertThat(((AppException) ex).type()).isEqualTo(ErrorType.STATE_CONFLICT))
			.hasMessageContaining("concurrently");
	}

	@Test
	void transitionPassesExpectedStatusAndVersion() {
		given(projectMapper.transition(eq(1L), eq(AssessmentStatus.READY), eq(AssessmentStatus.ANALYZING), eq(3), any()))
			.willReturn(1);

		assertThat(service.transition(project(AssessmentStatus.READY, 3), WorkflowEvent.START_ANALYSIS, null))
			.isEqualTo(AssessmentStatus.ANALYZING);
	}

}
