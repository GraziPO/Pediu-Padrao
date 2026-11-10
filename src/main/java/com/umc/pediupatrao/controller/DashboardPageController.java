package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Tela do dashboard administrativo. Os dados sao carregados pela pagina via /api/dashboard. */
@Controller
public class DashboardPageController {

    private final DashboardService dashboardService;

    public DashboardPageController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("statusPedido", StatusPedido.values());
        model.addAttribute("tiposProduto", dashboardService.tiposDeProduto());
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
