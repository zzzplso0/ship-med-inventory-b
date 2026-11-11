package com.shipmed.shipmed.domain.usage.service;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.exception.MedicationNotFoundException;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.LotStatus;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.usage.dto.UsageMapperImpl;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.AllocationDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.domain.usage.entity.UsageRecord;
import com.shipmed.shipmed.domain.usage.exception.UsageIdempotencyKeyReusedException;
import com.shipmed.shipmed.domain.usage.exception.UsageInsufficientStockException;
import com.shipmed.shipmed.domain.usage.repository.UsageLotAllocationRepository;
import com.shipmed.shipmed.domain.usage.repository.UsageRecordRepository;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shipmed.shipmed.FixedClockConfiguration.FIXED_CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsageServiceTest {

	private static final Long SHIP_ID = 1L;
	private static final Long MEDICATION_ID = 2L;
	private static final Long USER_ID = 3L;
	private static final Long EXISTING_USAGE_ID = 100L;
	private static final String KEY = "key-1";
	private static final LocalDate TODAY = LocalDate.parse("2026-07-22");
	private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

	@Mock
	private MedicationRepository medicationRepository;

	@Mock
	private StockLotRepository stockLotRepository;

	@Mock
	private UsageRecordRepository usageRecordRepository;

	@Mock
	private UsageLotAllocationRepository usageLotAllocationRepository;

	@Mock
	private ShipRepository shipRepository;

	@Mock
	private UserRepository userRepository;

	private UsageService usageService;
	private Ship ship;
	private Medication medication;

	@BeforeEach
	void setUp() {
		UsageProcessor usageProcessor = new UsageProcessor(
				medicationRepository, stockLotRepository, usageRecordRepository, usageLotAllocationRepository,
				shipRepository, userRepository, FIXED_CLOCK, new UsageMapperImpl());
		usageService = new UsageServiceImpl(usageProcessor);

		ship = new Ship("선박");
		ReflectionTestUtils.setField(ship, "id", SHIP_ID);
		medication = new Medication("아스피린", null, "정");
		ReflectionTestUtils.setField(medication, "id", MEDICATION_ID);
	}

	@Nested
	@DisplayName("사용 기록")
	class Create {

		@Test
		@DisplayName("유효기한이 빠른 로트부터 차감하고 로트별 분배를 남긴다")
		void givenTwoLots_whenUse40_thenDeductsEarliestExpiryFirst() {
			StockLot lotA = lot(10L, 100, "2027-01-31", "2026-03-01T00:00:00Z");
			StockLot lotB = lot(11L, 30, "2026-08-31", "2026-03-01T00:00:00Z");
			givenMedication();
			givenLockedLots(lotA, lotB);

			UsageResult result = create(40);

			assertThat(result.created()).isTrue();
			assertThat(result.usageDto().allocations())
					.containsExactly(new AllocationDto(11L, 30), new AllocationDto(10L, 10));
			assertThat(lotB.getRemainingQuantity()).isZero();
			assertThat(lotA.getRemainingQuantity()).isEqualTo(90);
		}

		@Test
		@DisplayName("유효기한이 같으면 id가 아니라 입고 시각이 빠른 로트부터 차감한다")
		void givenSameExpiry_whenUse_thenDeductsEarliestReceivedFirst() {
			// 잠금 쿼리는 id 순으로 돌려주므로, 입고가 늦은 Q의 id를 더 작게 둔다.
			StockLot lotQ = lot(20L, 20, "2026-08-01", "2026-05-01T00:00:00Z");
			StockLot lotP = lot(21L, 20, "2026-08-01", "2026-03-01T00:00:00Z");
			givenMedication();
			givenLockedLots(lotQ, lotP);

			UsageResult result = create(10);

			assertThat(result.usageDto().allocations()).containsExactly(new AllocationDto(21L, 10));
		}

		@Test
		@DisplayName("가용 재고가 부족하면 INSUFFICIENT_STOCK이고 차감·저장하지 않는다")
		void givenInsufficientStock_whenUse_thenThrowsAndKeepsLots() {
			StockLot lot = lot(10L, 3, "2027-01-31", "2026-03-01T00:00:00Z");
			givenMedication();
			givenLockedLots(lot);

			assertThatThrownBy(() -> create(5)).isInstanceOf(UsageInsufficientStockException.class);

			assertThat(lot.getRemainingQuantity()).isEqualTo(3);
			verify(usageRecordRepository, never()).saveAndFlush(any(UsageRecord.class));
		}

		@Test
		@DisplayName("같은 키로 처리한 요청이 있으면 잠그지 않고 기존 결과를 돌려준다")
		void givenSameKeyUsed_whenUse_thenReturnsExistingWithoutLock() {
			givenMedication();
			given(usageRecordRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
					.willReturn(Optional.of(existingUsage(40)));

			UsageResult result = create(40);

			assertThat(result.created()).isFalse();
			assertThat(result.usageDto().usageId()).isEqualTo(EXISTING_USAGE_ID);
			verify(stockLotRepository, never()).findAvailableForUpdate(any(), any(), any(), any());
		}

		@SuppressWarnings(value = "unchecked")
		@Test
		@DisplayName("잠금을 기다리는 동안 같은 키 요청이 커밋됐으면 차감하지 않고 기존 결과를 돌려준다")
		void givenSameKeyCommittedWhileLocking_whenUse_thenReturnsExisting() {
			StockLot lot = lot(10L, 100, "2027-01-31", "2026-03-01T00:00:00Z");
			givenMedication();
			givenLockedLots(lot);
			given(usageRecordRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
					.willReturn(Optional.empty(), Optional.of(existingUsage(40)));

			UsageResult result = create(40);

			assertThat(result.created()).isFalse();
			assertThat(lot.getRemainingQuantity()).isEqualTo(100);
			verify(usageRecordRepository, never()).saveAndFlush(any(UsageRecord.class));
		}

		@Test
		@DisplayName("같은 키로 수량이 다른 요청이면 IDEMPOTENCY_KEY_REUSED")
		void givenSameKeyWithOtherQuantity_whenUse_thenThrowsKeyReused() {
			givenMedication();
			given(usageRecordRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
					.willReturn(Optional.of(existingUsage(40)));

			assertThatThrownBy(() -> create(30)).isInstanceOf(UsageIdempotencyKeyReusedException.class);
		}

		@Test
		@DisplayName("없는 약품이면 멱등 판정 전에 MEDICATION_NOT_FOUND")
		void givenMissingMedication_whenUse_thenThrowsBeforeIdempotencyCheck() {
			given(medicationRepository.findById(MEDICATION_ID)).willReturn(Optional.empty());

			assertThatThrownBy(() -> create(40)).isInstanceOf(MedicationNotFoundException.class);

			verify(usageRecordRepository, never()).findByUserIdAndIdempotencyKey(any(), any());
		}

		@SuppressWarnings(value = "unchecked")
		@Test
		@DisplayName("uq_usage_idempotency 위반이면 다시 조회해 기존 결과를 돌려준다")
		void givenIdempotencyViolation_whenUse_thenReplays() {
			givenMedication();
			givenLockedLots(lot(10L, 100, "2027-01-31", "2026-03-01T00:00:00Z"));
			given(usageRecordRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
					.willReturn(Optional.empty(), Optional.empty(), Optional.of(existingUsage(40)));
			given(usageRecordRepository.saveAndFlush(any(UsageRecord.class)))
					.willThrow(violation("uq_usage_idempotency"));

			UsageResult result = create(40);

			assertThat(result.created()).isFalse();
			assertThat(result.usageDto().usageId()).isEqualTo(EXISTING_USAGE_ID);
		}

		@Test
		@DisplayName("uq_usage_idempotency 외의 제약 위반은 그대로 던진다")
		void givenOtherConstraintViolation_whenUse_thenRethrows() {
			givenMedication();
			givenLockedLots(lot(10L, 100, "2027-01-31", "2026-03-01T00:00:00Z"));
			DataIntegrityViolationException violation = violation("tb_usage_record_total_quantity_check");
			given(usageRecordRepository.saveAndFlush(any(UsageRecord.class))).willThrow(violation);

			assertThatThrownBy(() -> create(40)).isSameAs(violation);
		}

		private UsageResult create(int quantity) {
			return usageService.create(SHIP_ID, USER_ID, KEY, new UsageCreateRequest(MEDICATION_ID, quantity));
		}

		private void givenMedication() {
			given(medicationRepository.findById(MEDICATION_ID)).willReturn(Optional.of(medication));
		}

		private void givenLockedLots(StockLot... lots) {
			given(stockLotRepository.findAvailableForUpdate(SHIP_ID, MEDICATION_ID, LotStatus.ACTIVE, TODAY))
					.willReturn(List.of(lots));
		}

		private StockLot lot(Long id, int quantity, String expiryDate, String receivedAt) {
			StockLot lot = new StockLot("L-" + id, quantity, LocalDate.parse(expiryDate), Instant.parse(receivedAt),
					null, ship, medication);
			ReflectionTestUtils.setField(lot, "id", id);
			return lot;
		}

		private UsageRecord existingUsage(int quantity) {
			UsageRecord usageRecord = new UsageRecord(quantity, KEY, NOW, null, ship, medication);
			ReflectionTestUtils.setField(usageRecord, "id", EXISTING_USAGE_ID);
			return usageRecord;
		}

		private DataIntegrityViolationException violation(String constraintName) {
			return new DataIntegrityViolationException(
					"위반", new ConstraintViolationException("위반", null, constraintName));
		}
	}
}
