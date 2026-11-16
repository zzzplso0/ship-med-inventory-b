package com.shipmed.shipmed.domain.usage.controller;

import com.shipmed.shipmed.domain.usage.dto.request.UsageCancelRequest;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.UsageCancelDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.domain.usage.service.UsageService;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class UsageController {

    private final UsageService usageService;

    @PostMapping("/ships/{shipId}/usages")
    public ResponseEntity<UsageDto> create(@PathVariable Long shipId,
                                           @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
                                           @Valid @RequestBody UsageCreateRequest usageCreateRequest,
                                           @AuthenticationPrincipal CustomUserDetails customUserDetails)
    {
        Long userId = customUserDetails.getUserId();

        UsageResult result = usageService.create(shipId, userId, key, usageCreateRequest);

        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.usageDto());
        } else {
            return ResponseEntity.ok(result.usageDto());
        }
    }

    @PostMapping("/usages/{usageId}/cancellation")
    public ResponseEntity<UsageCancelDto> cancel(@PathVariable Long usageId,
                                                 @Valid @RequestBody UsageCancelRequest usageCancelRequest,
                                                 @AuthenticationPrincipal CustomUserDetails customUserDetails)
    {
        Long userId = customUserDetails.getUserId();

        UsageCancelDto result = usageService.cancel(usageId, userId, usageCancelRequest);

        return ResponseEntity.ok(result);
    }
}
