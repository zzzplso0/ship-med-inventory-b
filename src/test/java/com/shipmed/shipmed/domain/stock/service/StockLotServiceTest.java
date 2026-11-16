package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.dto.StockLotMapperImpl;
import com.shipmed.shipmed.domain.stock.dto.request.StockLotCreateRequest;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.exception.StockLotDuplicateException;
import com.shipmed.shipmed.domain.stock.exception.StockLotExpiredOnArrivalException;
import com.shipmed.shipmed.domain.stock.exception.StockLotReceivedAtInFutureException;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static com.shipmed.shipmed.FixedClockConfiguration.FIXED_CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockLotServiceTest {

	private static final Long SHIP_ID = 1L;
	private static final Long MEDICATION_ID = 2L;
	private static final Long USER_ID = 3L;
	private static final LocalDate TODAY = LocalDate.parse("2026-07-22");
	private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

	@Mock
	private StockLotRepository stockLotRepository;

	@Mock
	private ShipRepository shipRepository;

	@Mock
	private MedicationRepository medicationRepository;

	@Mock
	private UserRepository userRepository;

	private StockLotService stockLotService;

	@BeforeEach
	void setUp() {
		stockLotService = new StockLotServiceImpl(
				stockLotRepository, shipRepository, medicationRepository, userRepository,
				new StockLotMapperImpl(), FIXED_CLOCK);

		Medication medication = new Medication("약품", null, "정");
		ReflectionTestUtils.setField(medication, "id", MEDICATION_ID);

		given(medicationRepository.findById(MEDICATION_ID)).willReturn(Optional.of(medication));
	}

	@Nested
	@DisplayName("재고 입고")
	class Create {
		@Test
		@DisplayName("만료일이 오늘이고 입고 시각이 현재면 입고자를 넣어 저장한다")
		void givenBoundaryDates_whenCreate_thenSavesWithReceiver() {
			User user = new User("officer_1", "이름", "encoded", Role.MEDICAL_OFFICER, new Ship("선박"));
			given(userRepository.getReferenceById(USER_ID)).willReturn(user);

			create(TODAY, NOW);

			ArgumentCaptor<StockLot> captor = ArgumentCaptor.forClass(StockLot.class);
			verify(stockLotRepository).saveAndFlush(captor.capture());
			assertThat(captor.getValue().getReceivedBy()).isSameAs(user);
		}

		@Test
		@DisplayName("만료일이 오늘보다 과거면 EXPIRED_ON_ARRIVAL이고 저장하지 않는다")
		void givenPastExpiryDate_whenCreate_thenThrowsExpiredOnArrival() {
			assertThatThrownBy(() -> create(TODAY.minusDays(1), NOW))
					.isInstanceOf(StockLotExpiredOnArrivalException.class);

			verify(stockLotRepository, never()).saveAndFlush(any(StockLot.class));
		}

		@Test
		@DisplayName("입고 시각이 현재보다 미래면 RECEIVED_AT_IN_FUTURE이고 저장하지 않는다")
		void givenFutureReceivedAt_whenCreate_thenThrowsReceivedAtInFuture() {
			assertThatThrownBy(() -> create(TODAY, NOW.plusSeconds(1)))
					.isInstanceOf(StockLotReceivedAtInFutureException.class);

			verify(stockLotRepository, never()).saveAndFlush(any(StockLot.class));
		}

		@Test
		@DisplayName("uq_lot 위반은 DUPLICATE_LOT으로 바꾼다")
		void givenLotViolation_whenCreate_thenThrowsDuplicateLot() {
			given(stockLotRepository.saveAndFlush(any(StockLot.class))).willThrow(violation("uq_lot"));

			assertThatThrownBy(() -> create(TODAY, NOW)).isInstanceOf(StockLotDuplicateException.class);
		}

		@Test
		@DisplayName("uq_lot 외의 제약 위반은 409로 바꾸지 않고 그대로 던진다")
		void givenOtherConstraintViolation_whenCreate_thenRethrows() {
			DataIntegrityViolationException violation = violation("tb_stock_lot_initial_quantity_check");
			given(stockLotRepository.saveAndFlush(any(StockLot.class))).willThrow(violation);

			assertThatThrownBy(() -> create(TODAY, NOW)).isSameAs(violation);
		}

		private void create(LocalDate expiryDate, Instant receivedAt) {
			stockLotService.create(SHIP_ID,
					new StockLotCreateRequest("L-001", 10, MEDICATION_ID, receivedAt, expiryDate), USER_ID);
		}

		private DataIntegrityViolationException violation(String constraintName) {
			return new DataIntegrityViolationException(
					"위반", new ConstraintViolationException("위반", null, constraintName));
		}
	}
}
