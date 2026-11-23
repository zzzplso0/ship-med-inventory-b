package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.stock.dto.request.StockLotDisposalRequest;
import com.shipmed.shipmed.domain.stock.dto.request.StockLotListQuery;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotDisposalDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotDto;
import com.shipmed.shipmed.domain.stock.service.StockLotService;
import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.request.PageQuery;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@RequiredArgsConstructor
@RequestMapping("/ships/{shipId}/lots")
@Controller
public class LotViewController {

    private static final DateTimeFormatter RECEIVED_AT_FORMAT = DateTimeFormatter.ofPattern(LotForm.RECEIVED_AT_PATTERN);

    private final StockLotService stockLotService;
    private final Clock clock;

    @GetMapping
    public String form(@PathVariable Long shipId, Model model) {
        model.addAttribute("shipId", shipId);
        // 입고 시각 기본값은 현재(UTC).
        model.addAttribute("defaultReceivedAt", LocalDateTime.now(clock.withZone(ZoneOffset.UTC)).format(RECEIVED_AT_FORMAT));

        return "lots";
    }

    @PostMapping
    public String create(@PathVariable Long shipId, @Valid @ModelAttribute("form") LotForm form, BindingResult bindingResult,
                         @AuthenticationPrincipal CustomUserDetails principal, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return form(shipId, model);
        }

        try {
            StockLotDto lot = stockLotService.create(shipId, form.toRequest(), principal.getUserId());
            redirectAttributes.addFlashAttribute("message",
                    "입고했습니다. (로트 ID " + lot.lotId() + ", " + form.lotNumber() + ", " + lot.initialQuantity() + ")");
        } catch (BusinessException e) {
            // 만료된 유효기한, 미래 입고 시각(400), 없는 약품(404), 중복 로트(409)는 폼에 보여 준다.
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            return form(shipId, model);
        }

        return "redirect:/ships/{shipId}/lots";
    }

    // 관리자는 모든 선박, 의무사관은 소속 선박만(인터셉터). 없는 선박은 404 화면.
    @GetMapping("/list")
    public String list(@PathVariable Long shipId, @ModelAttribute("query") StockLotListQuery query,
                       @Valid @ModelAttribute PageQuery pageQuery, Model model) {
        model.addAttribute("shipId", shipId);
        model.addAttribute("lots", stockLotService.getLots(shipId, query, pageQuery));
        // 페이지를 넘겨도 필터가 유지되도록 필터를 담은 목록 경로를 넘긴다.
        model.addAttribute("pageUrl", UriComponentsBuilder.fromPath("/ships/{shipId}/lots/list")
                .queryParamIfPresent("medicationId", Optional.ofNullable(query.medicationId()))
                .queryParamIfPresent("status", Optional.ofNullable(query.status()))
                .buildAndExpand(shipId)
                .toUriString());

        return "lot-list";
    }

    // 성공·검증 실패 모두 목록으로 돌아간다. 필터·페이지는 유지하지 않는다(design 6.4).
    // 그 선박에 없는 로트(404)는 오류 화면.
    @PostMapping("/{lotId}/disposal")
    public String discard(@PathVariable Long shipId, @PathVariable Long lotId,
                          @Valid @ModelAttribute StockLotDisposalRequest disposalForm, BindingResult bindingResult,
                          @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // 메시지를 그 로트 행 옆에 보여 준다.
            redirectAttributes.addFlashAttribute("disposalErrorLotId", lotId);
            redirectAttributes.addFlashAttribute("disposalError", ViewErrors.fieldErrors(bindingResult).get("reason"));
            return "redirect:/ships/{shipId}/lots/list";
        }

        StockLotDisposalDto disposed = stockLotService.discard(shipId, lotId, disposalForm, principal.getUserId());
        redirectAttributes.addFlashAttribute("message", "로트 ID " + disposed.lotId() + "을(를) 폐기했습니다.");

        return "redirect:/ships/{shipId}/lots/list";
    }
}
