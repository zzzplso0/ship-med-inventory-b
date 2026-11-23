package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.usage.dto.request.UsageCancelRequest;
import com.shipmed.shipmed.domain.usage.dto.request.UsageHistoryQuery;
import com.shipmed.shipmed.domain.usage.dto.response.UsageCancelDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageHistoryDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.domain.usage.service.UsageService;
import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Controller
public class UsageViewController {

    private final UsageService usageService;

    @GetMapping("/ships/{shipId}/usages")
    public String form(@PathVariable Long shipId, Model model) {
        model.addAttribute("shipId", shipId);
        // 폼을 그릴 때마다 새 키. 같은 폼을 두 번 제출하면 같은 키라 한 번만 차감된다.
        // 오류로 다시 그릴 때는 기록이 생기지 않았으므로 새 키를 줘도 안전하다.
        model.addAttribute("idempotencyKey", UUID.randomUUID().toString());

        return "usages";
    }

    @PostMapping("/ships/{shipId}/usages")
    public String create(@PathVariable Long shipId, @Valid @ModelAttribute("form") UsageForm form, BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails principal, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return form(shipId, model);
        }

        try {
            UsageResult result = usageService.create(shipId, principal.getUserId(), form.idempotencyKey(), form.toRequest());
            redirectAttributes.addFlashAttribute("usage", result.usageDto());
            redirectAttributes.addFlashAttribute("medicationId", form.medicationId());
            redirectAttributes.addFlashAttribute("repeated", !result.created());
        } catch (BusinessException e) {
            // 재고 부족, 키 재사용(409), 없는 약품(404)은 폼에 보여 준다.
            model.addAttribute("errorMessage", errorMessage(e));
            return form(shipId, model);
        }

        return "redirect:/ships/{shipId}/usages/result";
    }

    // 사용 결과와 취소 폼. 결과는 flash로만 받으므로 새로고침하면 사용 화면으로 돌아간다(decisions 부차 23번).
    @GetMapping("/ships/{shipId}/usages/result")
    public String result(@PathVariable Long shipId, Model model) {
        if (!model.containsAttribute("usage")) {
            return "redirect:/ships/{shipId}/usages";
        }
        model.addAttribute("shipId", shipId);

        return "usage-result";
    }

    @PostMapping("/usages/{usageId}/cancellation")
    public String cancel(@PathVariable Long usageId, @Valid @ModelAttribute("cancelForm") UsageCancelRequest cancelForm,
                         BindingResult bindingResult, @AuthenticationPrincipal CustomUserDetails principal,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // 결과 데이터는 다시 읽지 않으므로 취소 폼만 다시 보여 준다.
            model.addAttribute("shipId", principal.getShipId());
            model.addAttribute("usageId", usageId);
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return "usage-result";
        }

        // 남의 기록(403), 없는 기록(404)은 오류 화면.
        UsageCancelDto cancelled = usageService.cancel(usageId, principal.getUserId(), cancelForm);
        redirectAttributes.addFlashAttribute("cancelled", cancelled);

        return "redirect:/ships/" + principal.getShipId() + "/usages";
    }

    private String errorMessage(BusinessException e) {
        if (e.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK) {
            return e.getErrorCode().getMessage()
                    + " (요청 " + e.getData().get("requested") + ", 가용 " + e.getData().get("available") + ")";
        }
        return e.getErrorCode().getMessage();
    }

    // 관리자는 모든 선박, 의무사관은 소속 선박만(인터셉터). 필터·페이지 오류는 BindingResult 없이 400 화면으로 보낸다(design 6.4).
    // 상세는 selected로 고른 기록을 현재 페이지의 결과에서 꺼낸다. 결과에 없으면 상세를 그리지 않는다.
    @GetMapping("/ships/{shipId}/usages/history")
    public String history(@PathVariable Long shipId, @Valid @ModelAttribute("query") UsageHistoryQuery query,
                          @Valid @ModelAttribute PageQuery pageQuery, @RequestParam(required = false) Long selected,
                          Model model) {
        PageResponse<UsageHistoryDto> history = usageService.getHistory(shipId, query, pageQuery);

        model.addAttribute("shipId", shipId);
        model.addAttribute("history", history);
        model.addAttribute("selectedUsage", history.content().stream()
                .filter(usage -> usage.usageId().equals(selected))
                .findFirst()
                .orElse(null));
        // 페이지를 넘겨도 필터가 유지되도록 필터를 담은 이력 경로를 넘긴다.
        model.addAttribute("pageUrl", historyUrl(shipId, query).toUriString());

        return "usage-history";
    }

    // 성공·사유 검증 실패 모두 취소 전의 필터·페이지를 유지하고 그 기록을 고른 이력 화면으로 돌아간다(design 6.4).
    // 남의 기록(403), 없는 기록(404)은 오류 화면.
    @PostMapping("/ships/{shipId}/usages/{usageId}/cancellation")
    public String cancelFromHistory(@PathVariable Long shipId, @PathVariable Long usageId,
                                    @Valid @ModelAttribute("cancelForm") UsageCancelRequest cancelForm, BindingResult bindingResult,
                                    @ModelAttribute("query") UsageHistoryQuery query, @ModelAttribute PageQuery pageQuery,
                                    @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("cancelError", ViewErrors.fieldErrors(bindingResult).get("reason"));
        } else {
            UsageCancelDto cancelled = usageService.cancel(usageId, principal.getUserId(), cancelForm);
            redirectAttributes.addFlashAttribute("message", "사용 기록 ID " + cancelled.usageId() + "을(를) 취소했습니다.");
        }

        return "redirect:" + historyUrl(shipId, query)
                .queryParam("page", pageQuery.page())
                .queryParam("size", pageQuery.size())
                .queryParam("selected", usageId)
                .toUriString();
    }

    private UriComponentsBuilder historyUrl(Long shipId, UsageHistoryQuery query) {
        return UriComponentsBuilder.fromPath("/ships/" + shipId + "/usages/history")
                .queryParamIfPresent("from", Optional.ofNullable(query.from()))
                .queryParamIfPresent("to", Optional.ofNullable(query.to()))
                .queryParamIfPresent("medicationId", Optional.ofNullable(query.medicationId()))
                .queryParamIfPresent("status", Optional.ofNullable(query.status()));
    }
}
