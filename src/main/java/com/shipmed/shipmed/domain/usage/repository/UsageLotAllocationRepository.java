package com.shipmed.shipmed.domain.usage.repository;

import com.shipmed.shipmed.domain.usage.entity.UsageLotAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsageLotAllocationRepository extends JpaRepository<UsageLotAllocation, Long> {

    // 사용기록의 로트별 분배내역.
    List<UsageLotAllocation> findByUsageRecordIdOrderById(Long usageId);
}
