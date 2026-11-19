package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.stock.dto.request.StockRequirementCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockRequirementResult;
import com.shipmed.shipmed.domain.stock.service.StockRequirementService;
import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;
import com.shipmed.shipmed.global.request.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RequiredArgsConstructor
@RequestMapping("/ships/{shipId}/requirements")
@Controller
public class RequirementViewController {

    private final StockRequirementService stockRequirementService;

    @GetMapping
    public String list(@PathVariable Long shipId, @Valid @ModelAttribute PageQuery pageQuery, Model model) {
        model.addAttribute("shipId", shipId);
        model.addAttribute("requirements", stockRequirementService.getRequirements(shipId, pageQuery));

        return "requirements";
    }

    @PostMapping
    public String upsert(@PathVariable Long shipId, @Valid @ModelAttribute("form") RequirementForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return list(shipId, new PageQuery(null, null), model);
        }

        try {
            StockRequirementResult result = stockRequirementService.upsertRequirement(
                    shipId, form.medicationId(), new StockRequirementCreateRequest(form.minQuantity()));
            redirectAttributes.addFlashAttribute("message",
                    result.created() ? "비치 기준을 새로 설정했습니다." : "비치 기준을 수정했습니다.");
        } catch (BusinessException e) {
            // 없는 선박은 화면 자체가 없으므로 404 오류 화면. 없는 약품(404), 신규 요청 겹침(409)은 폼에 보여 준다.
            if (e.getErrorCode() == ErrorCode.SHIP_NOT_FOUND) {
                throw e;
            }
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            return list(shipId, new PageQuery(null, null), model);
        }

        return "redirect:/ships/{shipId}/requirements";
    }
}
