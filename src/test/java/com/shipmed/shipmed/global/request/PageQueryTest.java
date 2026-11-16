package com.shipmed.shipmed.global.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@ParameterizedTest
	@CsvSource({"0,1", "21474836,100"})
	@DisplayName("범위의 경계값은 통과한다")
	void givenBoundaryValues_whenValidate_thenNoViolation(int page, int size) {
		assertThat(validator.validate(new PageQuery(page, size))).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({"-1,20", "21474837,20", "0,0", "0,101"})
	@DisplayName("범위를 벗어나면 위반이다")
	void givenOutOfRangeValues_whenValidate_thenViolation(int page, int size) {
		assertThat(validator.validate(new PageQuery(page, size))).hasSize(1);
	}
}
