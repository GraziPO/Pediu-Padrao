package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Respostas da API do dashboard administrativo (/api/dashboard).
 * Os valores monetarios sao calculados no servidor a partir dos pedidos gravados.
 */
public final class DashboardDto {

    private DashboardDto() {
    }

    /** RF01 - indicadores gerais do periodo filtrado, com o periodo anterior de mesmo tamanho para comparacao. */
    public record Resumo(
            LocalDate dataInicio,
            LocalDate dataFim,
            BigDecimal faturamento,
            long totalPedidos,
            long pedidosCancelados,
            BigDecimal ticketMedio,
            BigDecimal taxaCancelamento,
            Comparativo periodoAnterior) {
    }

    public record Comparativo(
            LocalDate dataInicio,
            LocalDate dataFim,
            BigDecimal faturamento,
            long totalPedidos,
            BigDecimal ticketMedio) {
    }

    /** RF02 - vendas agrupadas por dia (todos os dias do periodo aparecem, mesmo sem venda). */
    public record VendaDia(LocalDate data, long pedidos, BigDecimal faturamento) {
    }

    /** RF02 - vendas agrupadas pela hora de entrada do pedido (0 a 23). */
    public record VendaHora(int hora, long pedidos, BigDecimal faturamento) {
    }

    /** RF03 - quantidade de pedidos em cada status. */
    public record StatusQuantidade(String status, long quantidade, BigDecimal percentual) {
    }

    /** RF04 - ranking de produtos. */
    public record ProdutoVendido(String produtoId, String nome, String tipo, long quantidade, BigDecimal faturamento) {
    }

    /** RF04 - vendas por tipo de produto (Pizza, Esfiha, Bebida, Lanche...). */
    public record VendaTipo(String tipo, long quantidade, BigDecimal faturamento) {
    }

    /** RF03 - ultimos pedidos do periodo. */
    public record PedidoRecente(
            String id,
            LocalDateTime dataEntrada,
            String clienteId,
            String clienteNome,
            int quantidadeItens,
            BigDecimal valorTotal,
            String status) {
    }
}
