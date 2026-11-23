package com.shipmed.shipmed.domain.usage.dto.request;

import com.shipmed.shipmed.domain.usage.entity.RecordStatus;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;

public record UsageHistoryQuery(
        LocalDate from,
        LocalDate to,
        Long medicationId,
        RecordStatus status
) {
    @AssertTrue(message = "시작일은 종료일보다 늦을 수 없습니다.")
    public boolean isDateRangeValid() {
        return from == null || to == null || !from.isAfter(to);
    }
}
