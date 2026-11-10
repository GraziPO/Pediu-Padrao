package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDto.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDto.VendaDia;
import com.umc.pediupatrao.dto.DashboardDto.VendaHora;
import com.umc.pediupatrao.dto.DashboardDto.VendaTipo;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.DashboardService.Filtro;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * API do dashboard administrativo (RF06). Somente leitura; acesso de ADMIN e GERENTE (SecurityConfig).
 * Todos os endpoints aceitam os filtros dataInicio, dataFim (yyyy-MM-dd), status e tipo (RF05).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public Resumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.resumo(filtro(dataInicio, dataFim, status, tipo));
    }

    @GetMapping("/vendas-por-dia")
    public List<VendaDia> vendasPorDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.vendasPorDia(filtro(dataInicio, dataFim, status, tipo));
    }

    @GetMapping("/vendas-por-hora")
    public List<VendaHora> vendasPorHora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.vendasPorHora(filtro(dataInicio, dataFim, status, tipo));
    }

    @GetMapping("/status-pedidos")
    public List<StatusQuantidade> statusPedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.statusPedidos(filtro(dataInicio, dataFim, status, tipo));
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoVendido> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer limite,
            @RequestParam(required = false, defaultValue = "quantidade") String ordenarPor) {
        return dashboardService.produtosMaisVendidos(filtro(dataInicio, dataFim, status, tipo),
                DashboardService.validarLimite(limite, 5), ordenarPor);
    }

    @GetMapping("/vendas-por-tipo")
    public List<VendaTipo> vendasPorTipo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo) {
        return dashboardService.vendasPorTipo(filtro(dataInicio, dataFim, status, tipo));
    }

    @GetMapping("/pedidos-recentes")
    public List<PedidoRecente> pedidosRecentes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Integer limite) {
        return dashboardService.pedidosRecentes(filtro(dataInicio, dataFim, status, tipo),
                DashboardService.validarLimite(limite, 10));
    }

    private Filtro filtro(LocalDate dataInicio, LocalDate dataFim, String status, String tipo) {
        return dashboardService.criarFiltro(dataInicio, dataFim, status, tipo, LocalDate.now());
    }

    // Filtro invalido -> 400 com a mensagem (mesmo padrao do PedidoController)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> filtroInvalido(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body("Valor inválido para o parâmetro '" + e.getName() + "'. Use datas no formato aaaa-mm-dd.");
    }
}
