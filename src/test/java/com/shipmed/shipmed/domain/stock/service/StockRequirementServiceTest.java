package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.dto.StockRequirementMapperImpl;
import com.shipmed.shipmed.domain.stock.dto.request.StockRequirementCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementResult;
import com.shipmed.shipmed.domain.stock.entity.StockRequirement;
import com.shipmed.shipmed.domain.stock.repository.StockRequirementRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockRequirementServiceTest {

	private static final Long SHIP_ID = 1L;
	private static final Long MEDICATION_ID = 2L;

	@Mock
	private StockRequirementRepository stockRequirementRepository;

	@Mock
	private ShipRepository shipRepository;

	@Mock
	private MedicationRepository medicationRepository;

	private StockRequirementService stockRequirementService;
	private Ship ship;
	private Medication medication;

	@BeforeEach
	void setUp() {
		stockRequirementService = new StockRequirementServiceImpl(
				stockRequirementRepository, shipRepository, medicationRepository, new StockRequirementMapperImpl());

		ship = new Ship("선박");
		ReflectionTestUtils.setField(ship, "id", SHIP_ID);
		medication = new Medication("약품", null, "정");
		ReflectionTestUtils.setField(medication, "id", MEDICATION_ID);

		given(shipRepository.findById(SHIP_ID)).willReturn(Optional.of(ship));
		given(medicationRepository.findById(MEDICATION_ID)).willReturn(Optional.of(medication));
	}

	@Nested
	@DisplayName("비치 기준 설정")
	class UpsertRequirement {
		@Test
		@DisplayName("기존 행이 없으면 저장하고 신규로 반환한다")
		void givenNoRequirement_whenUpsert_thenSavesAndReturnsCreated() {
			StockRequirementResult result = upsert(5);

			verify(stockRequirementRepository).saveAndFlush(any(StockRequirement.class));
			assertThat(result.created()).isTrue();
			assertThat(result.stockRequirement()).isEqualTo(new StockRequirementDto(SHIP_ID, MEDICATION_ID, 5));
		}

		@Test
		@DisplayName("기존 행이 있으면 수량만 바꾸고 수정으로 반환한다")
		void givenRequirement_whenUpsert_thenChangesQuantityAndReturnsNotCreated() {
			given(stockRequirementRepository.findByShipIdAndMedicationId(SHIP_ID, MEDICATION_ID))
					.willReturn(new StockRequirement(5, ship, medication));

			StockRequirementResult result = upsert(7);

			verify(stockRequirementRepository, never()).saveAndFlush(any(StockRequirement.class));
			assertThat(result.created()).isFalse();
			assertThat(result.stockRequirement().minQuantity()).isEqualTo(7);
		}

		@Test
		@DisplayName("uq_requirement 외의 제약 위반은 409로 바꾸지 않고 그대로 던진다")
		void givenOtherConstraintViolation_whenUpsert_thenRethrows() {
			DataIntegrityViolationException violation = new DataIntegrityViolationException(
					"위반", new ConstraintViolationException("위반", null, "tb_stock_requirement_min_quantity_check"));
			given(stockRequirementRepository.saveAndFlush(any(StockRequirement.class))).willThrow(violation);

			assertThatThrownBy(() -> upsert(5)).isSameAs(violation);
		}

		private StockRequirementResult upsert(int minQuantity) {
			return stockRequirementService.upsertRequirement(
					SHIP_ID, MEDICATION_ID, new StockRequirementCreateRequest(minQuantity));
		}
	}
}
