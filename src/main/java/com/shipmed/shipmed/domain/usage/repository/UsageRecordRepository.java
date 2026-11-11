package com.shipmed.shipmed.domain.usage.repository;

import com.shipmed.shipmed.domain.usage.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    // 같은 사용자가 같은 Idempotency-Key로 남긴 사용기록.
    Optional<UsageRecord> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
