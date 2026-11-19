package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 사용 폼. idempotencyKey는 화면을 그릴 때 만들어 hidden 값으로 보낸다(design 6.4).
public record UsageForm(
        @NotNull Long medicationId,
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 100) String idempotencyKey
) {
    public UsageCreateRequest toRequest() {
        return new UsageCreateRequest(medicationId, quantity);
    }
}
