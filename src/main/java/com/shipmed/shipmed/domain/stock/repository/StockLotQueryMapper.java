package com.shipmed.shipmed.domain.stock.repository;

import com.shipmed.shipmed.domain.stock.dto.request.StockLotListQuery;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotListDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockLotQueryMapper {

    List<StockLotListDto> selectLots(@Param("shipId") Long shipId, @Param("query") StockLotListQuery query,
                                     @Param("today") LocalDate today, @Param("offset") int offset, @Param("limit") int limit);

    long countLots(@Param("shipId") Long shipId, @Param("query") StockLotListQuery query);
}
