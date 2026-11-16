package com.shipmed.shipmed.domain.medication.service;

import com.shipmed.shipmed.domain.medication.dto.MedicationMapperImpl;
import com.shipmed.shipmed.domain.medication.dto.request.MedicationCreateRequest;
import com.shipmed.shipmed.domain.medication.dto.response.MedicationDto;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

	@Mock
	private MedicationRepository medicationRepository;

	private MedicationService medicationService;

	@BeforeEach
	void setUp() {
		medicationService = new MedicationServiceImpl(medicationRepository, new MedicationMapperImpl());
	}

	@Nested
	@DisplayName("약품 등록")
	class Create {
		@Test
		@DisplayName("요청한 값으로 저장하고 저장된 값을 반환한다")
		void givenValidRequest_whenCreate_thenSavesFieldsAndReturnsDto() {
			given(medicationRepository.save(any(Medication.class))).willReturn(medication(1L));

			MedicationDto result = medicationService.create(new MedicationCreateRequest("약품1", "진통제", "정"));

			ArgumentCaptor<Medication> captor = ArgumentCaptor.forClass(Medication.class);
			verify(medicationRepository).save(captor.capture());
			assertThat(captor.getValue().getName()).isEqualTo("약품1");
			assertThat(captor.getValue().getCategory()).isEqualTo("진통제");
			assertThat(captor.getValue().getUnit()).isEqualTo("정");
			assertThat(result).isEqualTo(new MedicationDto(1L, "약품1", "진통제", "정"));
		}
	}

	@Nested
	@DisplayName("약품 목록")
	class GetAllMedications {
		@Test
		@DisplayName("요청한 페이지를 id 오름차순으로 조회하고 페이지 응답으로 변환한다")
		void givenPageQuery_whenGetAllMedications_thenQueriesSortedByIdAndReturnsPageResponse() {
			Pageable pageable = PageRequest.of(1, 2, Sort.by("id"));
			given(medicationRepository.findAll(pageable))
					.willReturn(new PageImpl<>(List.of(medication(3L)), pageable, 3));

			PageResponse<MedicationDto> result = medicationService.getAllMedications(new PageQuery(1, 2));

			assertThat(result.content()).containsExactly(new MedicationDto(3L, "약품3", "진통제", "정"));
			assertThat(result.totalElements()).isEqualTo(3);
			assertThat(result.hasNext()).isFalse();
		}
	}

	private Medication medication(Long id) {
		Medication medication = new Medication("약품" + id, "진통제", "정");
		ReflectionTestUtils.setField(medication, "id", id);
		return medication;
	}
}
