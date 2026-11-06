package com.shipmed.shipmed.domain.ship.service;

import com.shipmed.shipmed.domain.ship.dto.ShipMapperImpl;
import com.shipmed.shipmed.domain.ship.dto.request.ShipCreateRequest;
import com.shipmed.shipmed.domain.ship.dto.response.ShipDto;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
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
class ShipServiceTest {

	@Mock
	private ShipRepository shipRepository;

	private ShipService shipService;

	@BeforeEach
	void setUp() {
		shipService = new ShipServiceImpl(shipRepository, new ShipMapperImpl());
	}

	@Nested
	@DisplayName("선박 등록")
	class Create {
		@Test
		@DisplayName("요청한 이름으로 저장하고 저장된 id와 name을 반환한다")
		void givenValidRequest_whenCreate_thenSavesNameAndReturnsIdAndName() {
			given(shipRepository.save(any(Ship.class))).willReturn(ship(1L, "선박A"));

			ShipDto result = shipService.create(new ShipCreateRequest("선박A"));

			ArgumentCaptor<Ship> captor = ArgumentCaptor.forClass(Ship.class);
			verify(shipRepository).save(captor.capture());
			assertThat(captor.getValue().getName()).isEqualTo("선박A");
			assertThat(result).isEqualTo(new ShipDto(1L, "선박A"));
		}
	}

	@Nested
	@DisplayName("선박 목록")
	class GetAllShips {
		@Test
		@DisplayName("요청한 페이지를 id 오름차순으로 조회하고 페이지 응답으로 변환한다")
		void givenPageQuery_whenGetAllShips_thenQueriesSortedByIdAndReturnsPageResponse() {
			Pageable pageable = PageRequest.of(1, 2, Sort.by("id"));
			given(shipRepository.findAll(pageable))
					.willReturn(new PageImpl<>(List.of(ship(3L, "선박3"), ship(4L, "선박4")), pageable, 5));

			PageResponse<ShipDto> result = shipService.getAllShips(new PageQuery(1, 2));

			assertThat(result.content()).containsExactly(new ShipDto(3L, "선박3"), new ShipDto(4L, "선박4"));
			assertThat(result.page()).isEqualTo(1);
			assertThat(result.size()).isEqualTo(2);
			assertThat(result.totalElements()).isEqualTo(5);
			assertThat(result.totalPages()).isEqualTo(3);
			assertThat(result.hasNext()).isTrue();
		}

		@Test
		@DisplayName("page와 size를 생략하면 기본값으로 조회한다")
		void givenNullPageAndSize_whenGetAllShips_thenQueriesWithDefaults() {
			Pageable pageable = PageRequest.of(PageQuery.DEFAULT_PAGE, PageQuery.DEFAULT_SIZE, Sort.by("id"));
			given(shipRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(), pageable, 0));

			PageResponse<ShipDto> result = shipService.getAllShips(new PageQuery(null, null));

			assertThat(result.content()).isEmpty();
			assertThat(result.hasNext()).isFalse();
		}
	}

	private Ship ship(Long id, String name) {
		Ship ship = new Ship(name);
		ReflectionTestUtils.setField(ship, "id", id);
		return ship;
	}
}
