package com.shipmed.shipmed.view;

import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryDto;
import com.shipmed.shipmed.domain.stock.service.StockInventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class InventoryViewController {

    private final StockInventoryService stockInventoryService;

    // 관리자는 모든 선박, 의무사관은 소속 선박만(인터셉터). 없는 선박은 404 화면.
    @GetMapping("/ships/{shipId}/inventory")
    public String inventory(@PathVariable Long shipId, Model model) {
        List<StockInventoryDto> items = stockInventoryService.getInventory(shipId);

        model.addAttribute("shipId", shipId);
        model.addAttribute("items", items);
        model.addAttribute("shortageCount", items.stream().filter(item -> Boolean.TRUE.equals(item.shortage())).count());
        // 임박·폐기 대상은 로트 단위라 약품 수가 아니라 로트 수로 센다.
        model.addAttribute("expiringCount", items.stream().mapToInt(item -> item.expiringSoon().size()).sum());
        model.addAttribute("expiredCount", items.stream().mapToInt(item -> item.expired().size()).sum());

        return "inventory";
    }

    // 관리자의 선박 선택 폼(GET ?shipId=)을 선박 경로로 바꾼다. 소속 선박 판정은 이동한 경로에서 한다.
    @GetMapping("/inventory")
    public String selectShip(@RequestParam Long shipId) {
        return "redirect:/ships/" + shipId + "/inventory";
    }
}
