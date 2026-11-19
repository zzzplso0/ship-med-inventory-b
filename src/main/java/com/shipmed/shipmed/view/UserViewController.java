package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.user.dto.request.UserCreateRequest;
import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.domain.user.service.UserService;
import com.shipmed.shipmed.global.exception.BusinessException;
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
@RequestMapping("/users")
@Controller
public class UserViewController {

    private final UserService userService;

    @GetMapping
    public String list(@Valid @ModelAttribute PageQuery pageQuery, Model model) {
        model.addAttribute("users", userService.getAllUsers(pageQuery));

        return "users";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserCreateRequest form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("fieldErrors", ViewErrors.fieldErrors(bindingResult));
            return list(new PageQuery(null, null), model);
        }

        try {
            UserDto user = userService.create(form);
            redirectAttributes.addFlashAttribute("message", "계정을 등록했습니다. (" + user.loginId() + ")");
        } catch (BusinessException e) {
            // 로그인 ID 중복(409), 없는 선박(404)은 입력 오류로 보고 폼에 보여 준다.
            model.addAttribute("errorMessage", e.getErrorCode().getMessage());
            return list(new PageQuery(null, null), model);
        }

        return "redirect:/users";
    }
}
