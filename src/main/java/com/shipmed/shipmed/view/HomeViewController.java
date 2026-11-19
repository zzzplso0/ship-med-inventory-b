package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeViewController {

    // 역할별 첫 화면. 관리자는 선박 목록, 의무사관은 소속 선박의 재고 현황.
    @GetMapping("/")
    public String home(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal.getRole() == Role.ADMIN) {
            return "redirect:/ships";
        }

        return "redirect:/ships/" + principal.getShipId() + "/inventory";
    }
}
