package com.cryptoassess.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import com.cryptoassess.qa.CitationValidator.CitationCheck;
import com.cryptoassess.qa.CitationValidator.InvalidCitation;
import org.junit.jupiter.api.Test;

class CitationValidatorTest {

	private static final Set<String> IN_KB = Set.of("F#1", "F#2", "F#3");

	private final CitationValidator validator = new CitationValidator(IN_KB::contains);

	@Test
	void onlyRefsFromThisRetrievalAreValid() {
		CitationCheck check = validator.validate(List.of("F#1", "F#3", "F#9"), List.of("F#1", "F#2"));

		assertThat(check.valid()).containsExactly("F#1");
		assertThat(check.invalid()).containsExactly(
				new InvalidCitation("F#3", CitationValidator.Reason.NOT_IN_RESULTS),
				new InvalidCitation("F#9", CitationValidator.Reason.NOT_FOUND));
		assertThat(check.invalidCount()).isEqualTo(2);
	}

	@Test
	void noCitationsIsEmptyCheck() {
		CitationCheck check = validator.validate(List.of(), List.of("F#1"));

		assertThat(check.valid()).isEmpty();
		assertThat(check.invalid()).isEmpty();
	}

}
