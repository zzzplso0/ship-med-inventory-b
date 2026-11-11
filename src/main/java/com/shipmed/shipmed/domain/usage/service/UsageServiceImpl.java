package com.shipmed.shipmed.domain.usage.service;

import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UsageServiceImpl implements UsageService {

    private static final String UQ_USAGE_IDEMPOTENCY = "uq_usage_idempotency";

    private final UsageProcessor usageProcessor;

    @Override
    public UsageResult create(Long shipId, Long userId, String idempotencyKey, UsageCreateRequest usageCreateRequest) {
        try {
            return usageProcessor.record(shipId, userId, idempotencyKey, usageCreateRequest);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cause
                    && UQ_USAGE_IDEMPOTENCY.equals(cause.getConstraintName())) {
                return usageProcessor.replay(shipId, userId, idempotencyKey, usageCreateRequest);
            }

            throw e;
        }
    }
}
