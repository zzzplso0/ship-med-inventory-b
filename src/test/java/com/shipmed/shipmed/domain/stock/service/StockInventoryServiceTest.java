package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.ship.exception.ShipNotFoundException;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.dto.response.StockExpiringLotDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockExpiringLotRow;
import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryRow;
import com.shipmed.shipmed.domain.stock.repository.StockInventoryQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static com.shipmed.shipmed.FixedClockConfiguration.FIXED_CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 오늘 = 2026-07-22(UTC). 임박 상한 = 2026-08-21.
@ExtendWith(MockitoExtension.class)
class StockInventoryServiceTest {

	private static final Long SHIP_ID = 1L;
	private static final LocalDate TODAY = LocalDate.parse("2026-07-22");
	private static final LocalDate EXPIRING_UNTIL = LocalDate.parse("2026-08-21");

	@Mock
	private StockInventoryQueryMapper stockInventoryQueryMapper;

	@Mock
	private ShipRepository shipRepository;

	private StockInventoryService stockInventoryService;

	@BeforeEach
	void setUp() {
		stockInventoryService = new StockInventoryServiceImpl(stockInventoryQueryMapper, shipRepository, FIXED_CLOCK);
	}

	@Nested
	@DisplayName("재고 현황 조회")
	class GetInventory {
		@Test
		@DisplayName("오늘과 30일 뒤를 넘겨 조회하고, 임박 로트를 약품별로 붙이며 없는 약품은 빈 목록이다")
		void givenRowsAndExpiringLot_whenGetInventory_thenAttachesExpiringSoonByMedication() {
			given(shipRepository.existsById(SHIP_ID)).willReturn(true);
			given(stockInventoryQueryMapper.selectInventory(SHIP_ID, TODAY)).willReturn(List.of(
					new StockInventoryRow(1L, "모르핀", "앰플", 0, true, 5, true),
					new StockInventoryRow(2L, "아스피린", "정", 10, false, null, null)));
			given(stockInventoryQueryMapper.selectExpiringSoonLots(SHIP_ID, TODAY, EXPIRING_UNTIL)).willReturn(List.of(
					new StockExpiringLotRow(2L, 100L, 10, LocalDate.parse("2026-08-01"))));

			List<StockInventoryDto> result = stockInventoryService.getInventory(SHIP_ID);

			assertThat(result).extracting(StockInventoryDto::medicationId).containsExactly(1L, 2L);
			assertThat(result.get(0).expiringSoon()).isEmpty();
			assertThat(result.get(1).expiringSoon())
					.containsExactly(new StockExpiringLotDto(100L, 10, LocalDate.parse("2026-08-01")));
		}

		@Test
		@DisplayName("선박이 없으면 ShipNotFoundException이고 조회하지 않는다")
		void givenMissingShip_whenGetInventory_thenThrowsShipNotFound() {
			given(shipRepository.existsById(SHIP_ID)).willReturn(false);

			assertThatThrownBy(() -> stockInventoryService.getInventory(SHIP_ID))
					.isInstanceOf(ShipNotFoundException.class);
			verify(stockInventoryQueryMapper, never()).selectInventory(any(), any());
		}
	}
}
