package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.ship.dto.request.ShipCreateRequest;
import com.shipmed.shipmed.domain.ship.dto.response.ShipDto;
import com.shipmed.shipmed.domain.ship.service.ShipService;
import com.shipmed.shipmed.global.request.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RequiredArgsConstructor
@RequestMapping("/ships")
@Controller
public class ShipViewController {

    private final ShipService shipService;

    @GetMapping
    public String list(@Valid @ModelAttribute PageQuery pageQuery, Model model) {
        model.addAttribute("ships", shipService.getAllShips(pageQuery));

        return "ships";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ShipCreateRequest form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return list(new PageQuery(null, null), model);
        }

        ShipDto ship = shipService.create(form);
        redirectAttributes.addFlashAttribute("message", "선박을 등록했습니다. (ID " + ship.id() + ")");

        return "redirect:/ships";
    }
}
