package com.shipmed.shipmed.domain.usage.repository;

import com.shipmed.shipmed.domain.usage.dto.request.UsageHistoryQuery;
import com.shipmed.shipmed.domain.usage.dto.response.UsageHistoryAllocationRow;
import com.shipmed.shipmed.domain.usage.dto.response.UsageHistoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface UsageHistoryQueryMapper {

    List<UsageHistoryRow> selectUsages(@Param("shipId") Long shipId, @Param("query") UsageHistoryQuery query,
                                       @Param("fromAt") Instant fromAt, @Param("toBefore")Instant toBefore,
                                       @Param("offset") int offset, @Param("limit") int limit);

    long countUsages(@Param("shipId") Long shipId, @Param("query") UsageHistoryQuery query,
                     @Param("fromAt") Instant fromAt, @Param("toBefore") Instant toBefore);

    List<UsageHistoryAllocationRow> selectAllocations(@Param("usageIds") List<Long> usageIds);
}
