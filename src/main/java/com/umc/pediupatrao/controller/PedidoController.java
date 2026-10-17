package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoService pedidoService;

    @PostMapping
    public Pedido criarPedido(@RequestBody Pedido pedido) {
        return pedidoService.criarPedido(pedido);
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    // Exclusivo do GERENTE (SecurityConfig + @PreAuthorize no PedidoService)
    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestParam BigDecimal percentual) {
        return pedidoService.aplicarDesconto(id, percentual);
    }

    // Exclusivo do GERENTE (SecurityConfig + @PreAuthorize no PedidoService)
    @PostMapping("/{id}/cancelar")
    public Pedido cancelarPedido(@PathVariable String id, @RequestParam String justificativa) {
        return pedidoService.cancelarPedido(id, justificativa);
    }

    // Regra de negocio violada (transicao invalida, desconto acima do limite etc.) -> 400 com a mensagem
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> regraViolada(RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
    
    
}
