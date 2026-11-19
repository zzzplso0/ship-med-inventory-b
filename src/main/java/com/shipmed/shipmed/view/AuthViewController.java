package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.auth.dto.LoginRequest;
import com.shipmed.shipmed.domain.auth.service.AuthService;
import com.shipmed.shipmed.domain.auth.session.LoginSessionManager;
import com.shipmed.shipmed.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@RequiredArgsConstructor
@Controller
public class AuthViewController {

    private final AuthService authService;
    private final LoginSessionManager loginSessionManager;

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest request, BindingResult bindingResult, Model model,
                        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        if (bindingResult.hasErrors()) {
            return loginFormWithError(model, request.loginId(), "로그인 ID와 비밀번호를 입력해 주세요.");
        }

        try {
            Authentication authentication = authService.login(request);
            loginSessionManager.saveContext(authentication, httpServletRequest, httpServletResponse);
        } catch (BusinessException e) {
            return loginFormWithError(model, request.loginId(), e.getErrorCode().getMessage());
        }

        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(Authentication authentication, HttpServletRequest httpServletRequest,
                         HttpServletResponse httpServletResponse) {
        loginSessionManager.logout(authentication, httpServletRequest, httpServletResponse);

        return "redirect:/login";
    }

    // 실패하면 로그인 ID만 다시 채우고, 비밀번호는 비운다.
    private String loginFormWithError(Model model, String loginId, String errorMessage) {
        model.addAttribute("loginId", loginId);
        model.addAttribute("errorMessage", errorMessage);

        return "login";
    }
}
