package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.medication.dto.request.MedicationCreateRequest;
import com.shipmed.shipmed.domain.medication.dto.response.MedicationDto;
import com.shipmed.shipmed.domain.medication.service.MedicationService;
import com.shipmed.shipmed.global.request.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RequiredArgsConstructor
@RequestMapping("/medications")
@Controller
public class MedicationViewController {

    private final MedicationService medicationService;

    // 폼은 빈 칸도 빈 문자열로 보내므로, 앞뒤 공백을 지우고 빈 값은 null(생략)로 받는다. 분류(category)를 비우면 생략으로 본다.
    @InitBinder("form")
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping
    public String list(@Valid @ModelAttribute PageQuery pageQuery, Model model) {
        model.addAttribute("medications", medicationService.getAllMedications(pageQuery));

        return "medications";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") MedicationCreateRequest form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return list(new PageQuery(null, null), model);
        }

        MedicationDto medication = medicationService.create(form);
        redirectAttributes.addFlashAttribute("message", "약품을 등록했습니다. (ID " + medication.id() + ")");

        return "redirect:/medications";
    }
}
