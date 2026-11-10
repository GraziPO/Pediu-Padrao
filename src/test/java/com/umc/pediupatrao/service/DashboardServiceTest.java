package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardDto.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDto.VendaDia;
import com.umc.pediupatrao.dto.DashboardDto.VendaHora;
import com.umc.pediupatrao.dto.DashboardDto.VendaTipo;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.service.DashboardService.Filtro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Regras de calculo do dashboard. O MongoTemplate e substituido por um stub; a data "hoje" e fixa. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 9);

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private DashboardService service;

    private final List<Pedido> pedidosDoPeriodo = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(produtoRepository.findAll()).thenReturn(List.of(
                produto("pz1", "Pizza Calabresa", "Pizza"),
                produto("bb1", "Refrigerante 2L", "Bebida")));
        when(mongoTemplate.find(any(Query.class), eq(Pedido.class))).thenReturn(pedidosDoPeriodo);
    }

    // ---------- Filtros (RF05) ----------

    @Test
    void semDatasUsaOsUltimosSeteDiasAteHoje() {
        Filtro f = service.criarFiltro(null, null, null, null, HOJE);
        assertEquals(LocalDate.of(2026, 11, 3), f.inicio());
        assertEquals(HOJE, f.fim());
        assertEquals(7, f.dias());
    }

    @Test
    void dataInicialDepoisDaFinalEhRecusada() {
        assertThrows(IllegalArgumentException.class,
                () -> service.criarFiltro(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 9), null, null, HOJE));
    }

    @Test
    void periodoDe366DiasEhAceitoE367EhRecusado() {
        LocalDate fim = LocalDate.of(2026, 11, 9);
        assertEquals(366, service.criarFiltro(fim.minusDays(365), fim, null, null, HOJE).dias());
        assertThrows(IllegalArgumentException.class, () -> service.criarFiltro(fim.minusDays(366), fim, null, null, HOJE));
    }

    @Test
    void statusInexistenteEhRecusado() {
        assertThrows(IllegalArgumentException.class, () -> service.criarFiltro(null, null, "ENTREGUE", null, HOJE));
    }

    @Test
    void statusEmMinusculoEhAceito() {
        assertEquals(StatusPedido.EM_PREPARACAO, service.criarFiltro(null, null, "em_preparacao", null, HOJE).status());
    }

    @Test
    void limiteForaDaFaixaEhRecusado() {
        assertEquals(5, DashboardService.validarLimite(null, 5));
        assertThrows(IllegalArgumentException.class, () -> DashboardService.validarLimite(0, 5));
        assertThrows(IllegalArgumentException.class, () -> DashboardService.validarLimite(51, 5));
    }

    // ---------- RF01 ----------

    @Test
    void resumoNaoSomaPedidosCanceladosNoFaturamento() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "100.00", item("pz1", "Pizza Calabresa", 2, "50.00")));
        pedidosDoPeriodo.add(pedido("2", "FINALIZADO", "2026-11-08T21:00", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("3", "CANCELADO", "2026-11-09T19:00", "80.00", item("pz1", "Pizza Calabresa", 1, "80.00")));
        pedidosDoPeriodo.add(pedido("4", "RECEBIDO", "2026-11-09T19:30", "30.00", item("bb1", "Refrigerante 2L", 2, "15.00")));

        Resumo r = service.resumo(filtroSemana());

        assertEquals(new BigDecimal("180.00"), r.faturamento());
        assertEquals(4, r.totalPedidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(new BigDecimal("60.00"), r.ticketMedio());       // 180 / 3 pedidos validos
        assertEquals(new BigDecimal("25.00"), r.taxaCancelamento());  // 1 de 4
    }

    @Test
    void resumoSemPedidosRetornaZerosSemDividirPorZero() {
        Resumo r = service.resumo(filtroSemana());
        assertEquals(new BigDecimal("0.00"), r.faturamento());
        assertEquals(new BigDecimal("0.00"), r.ticketMedio());
        assertEquals(0, r.taxaCancelamento().signum());
    }

    @Test
    void periodoAnteriorTemOMesmoTamanhoEAcabaUmDiaAntes() {
        Resumo r = service.resumo(filtroSemana());
        assertEquals(LocalDate.of(2026, 10, 27), r.periodoAnterior().dataInicio());
        assertEquals(LocalDate.of(2026, 11, 2), r.periodoAnterior().dataFim());
    }

    @Test
    void comFiltroDeTipoFaturamentoConsideraSoOsItensDoTipo() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "65.00",
                item("pz1", "Pizza Calabresa", 1, "50.00"), item("bb1", "Refrigerante 2L", 1, "15.00")));
        pedidosDoPeriodo.add(pedido("2", "FINALIZADO", "2026-11-08T21:00", "30.00", item("pz1", "Pizza Calabresa", 1, "30.00")));
        pedidosDoPeriodo.add(pedido("3", "FINALIZADO", "2026-11-08T22:00", "15.00", item("bb1", "Refrigerante 2L", 1, "15.00")));

        Resumo r = service.resumo(service.criarFiltro(LocalDate.of(2026, 11, 3), HOJE, null, "bebida", HOJE));

        assertEquals(2, r.totalPedidos());                       // pedidos 1 e 3 tem bebida
        assertEquals(new BigDecimal("30.00"), r.faturamento());  // 15 + 15
    }

    // ---------- RF02 ----------

    @Test
    void vendasPorDiaTrazTodosOsDiasDoPeriodoMesmoSemVenda() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "100.00", item("pz1", "Pizza Calabresa", 2, "50.00")));

        List<VendaDia> dias = service.vendasPorDia(filtroSemana());

        assertEquals(7, dias.size());
        VendaDia dia8 = dias.stream().filter(d -> d.data().equals(LocalDate.of(2026, 11, 8))).findFirst().orElseThrow();
        assertEquals(1, dia8.pedidos());
        assertEquals(new BigDecimal("100.00"), dia8.faturamento());
        assertEquals(0, dias.get(0).pedidos());
    }

    @Test
    void vendasPorHoraAgrupaPelaHoraDeEntradaEIgnoraCancelados() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:10", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("2", "FINALIZADO", "2026-11-08T20:59", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("3", "CANCELADO", "2026-11-08T20:30", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));

        List<VendaHora> horas = service.vendasPorHora(filtroSemana());

        assertEquals(24, horas.size());
        assertEquals(2, horas.get(20).pedidos());
        assertEquals(0, horas.get(21).pedidos());
    }

    // ---------- RF03 ----------

    @Test
    void statusPedidosListaTodosOsStatusComPercentual() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("2", "FINALIZADO", "2026-11-08T20:00", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("3", "CANCELADO", "2026-11-08T20:00", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("4", "RECEBIDO", "2026-11-08T20:00", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));

        List<StatusQuantidade> status = service.statusPedidos(filtroSemana());

        assertEquals(StatusPedido.values().length, status.size());
        StatusQuantidade finalizado = status.stream().filter(s -> s.status().equals("FINALIZADO")).findFirst().orElseThrow();
        assertEquals(2, finalizado.quantidade());
        assertEquals(new BigDecimal("50.00"), finalizado.percentual());
    }

    // ---------- RF04 ----------

    @Test
    void rankingSomaQuantidadesDoMesmoProdutoEIgnoraCancelados() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "100.00", item("pz1", "Pizza Calabresa", 2, "50.00")));
        pedidosDoPeriodo.add(pedido("2", "FINALIZADO", "2026-11-08T21:00", "50.00", item("pz1", "Pizza Calabresa", 1, "50.00")));
        pedidosDoPeriodo.add(pedido("3", "FINALIZADO", "2026-11-08T21:00", "60.00", item("bb1", "Refrigerante 2L", 4, "15.00")));
        pedidosDoPeriodo.add(pedido("4", "CANCELADO", "2026-11-08T21:00", "150.00", item("bb1", "Refrigerante 2L", 10, "15.00")));

        List<ProdutoVendido> porQuantidade = service.produtosMaisVendidos(filtroSemana(), 5, "quantidade");
        List<ProdutoVendido> porFaturamento = service.produtosMaisVendidos(filtroSemana(), 5, "faturamento");

        assertEquals("Refrigerante 2L", porQuantidade.get(0).nome());
        assertEquals(4, porQuantidade.get(0).quantidade());
        assertEquals("Pizza Calabresa", porFaturamento.get(0).nome());
        assertEquals(new BigDecimal("150.00"), porFaturamento.get(0).faturamento());
        assertEquals("Pizza", porFaturamento.get(0).tipo());
    }

    @Test
    void rankingRespeitaOLimite() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "65.00",
                item("pz1", "Pizza Calabresa", 1, "50.00"), item("bb1", "Refrigerante 2L", 1, "15.00")));
        assertEquals(1, service.produtosMaisVendidos(filtroSemana(), 1, null).size());
    }

    @Test
    void ordenacaoInvalidaDoRankingEhRecusada() {
        assertThrows(IllegalArgumentException.class, () -> service.produtosMaisVendidos(filtroSemana(), 5, "preco"));
    }

    @Test
    void vendasPorTipoUsaOTipoDoCadastroDoProduto() {
        pedidosDoPeriodo.add(pedido("1", "FINALIZADO", "2026-11-08T20:00", "65.00",
                item("pz1", "Pizza Calabresa", 1, "50.00"), item("bb1", "Refrigerante 2L", 1, "15.00"),
                item("xx9", "Produto excluído", 1, "10.00")));

        List<VendaTipo> tipos = service.vendasPorTipo(filtroSemana());

        assertEquals("Pizza", tipos.get(0).tipo());
        assertTrue(tipos.stream().anyMatch(t -> t.tipo().equals("Sem tipo")));
    }

    // ---------- Apoio ----------

    private Filtro filtroSemana() {
        return service.criarFiltro(LocalDate.of(2026, 11, 3), HOJE, null, null, HOJE);
    }

    private static Produto produto(String id, String nome, String tipo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(10.0);
        p.setAtivo(true);
        return p;
    }

    private static ItemPedido item(String produtoId, String nome, int quantidade, String preco) {
        ItemPedido i = new ItemPedido();
        i.setProdutoId(produtoId);
        i.setNomeProduto(nome);
        i.setQuantidade(quantidade);
        i.setPrecoUnitario(new BigDecimal(preco));
        i.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(quantidade)));
        return i;
    }

    private static Pedido pedido(String id, String status, String entrada, String total, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteId("c1");
        p.setStatus(status);
        p.setDataEntrada(LocalDateTime.parse(entrada));
        p.setValorTotal(new BigDecimal(total));
        p.setItens(new ArrayList<>(List.of(itens)));
        return p;
    }
}
