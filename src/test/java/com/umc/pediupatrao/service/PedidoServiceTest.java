package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @BeforeEach
    void setUp() {
        when(auditoriaService.usuarioLogado()).thenReturn("atendente01");
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Pedido pedidoComStatus(String status) {
        ItemPedido item = new ItemPedido();
        item.setNomeProduto("Pizza");
        item.setPrecoUnitario(new BigDecimal("50.00"));
        item.setQuantidade(2);
        Pedido pedido = new Pedido();
        pedido.setId("p1");
        pedido.setStatus(status);
        pedido.getItens().add(item);
        when(pedidoRepository.findById("p1")).thenReturn(Optional.of(pedido));
        return pedido;
    }

    @Test
    void criarPedidoCalculaTotalNoServidorComPrecoDoCadastro() {
        Produto produto = new Produto();
        produto.setId("pz");
        produto.setNome("Calabresa");
        produto.setPreco(49.90);
        produto.setAtivo(true);
        when(clienteRepository.existsById("c1")).thenReturn(true);
        when(produtoRepository.findById("pz")).thenReturn(Optional.of(produto));

        ItemPedido item = new ItemPedido();
        item.setProdutoId("pz");
        item.setQuantidade(3);
        item.setPrecoUnitario(new BigDecimal("0.01")); // valor enviado pelo cliente deve ser ignorado
        Pedido pedido = new Pedido();
        pedido.setClienteId("c1");
        pedido.getItens().add(item);
        pedido.setValorTotal(new BigDecimal("1.00")); // idem

        Pedido salvo = pedidoService.criarPedido(pedido);

        assertEquals(0, new BigDecimal("149.70").compareTo(salvo.getValorTotal()));
        assertEquals("RECEBIDO", salvo.getStatus());
        assertEquals("atendente01", salvo.getRegistradoPor());
        assertNotNull(salvo.getDataEntrada());
    }

    @Test
    void transicaoInvalidaEhBloqueada() {
        pedidoComStatus("EM_PREPARACAO");
        assertThrows(IllegalStateException.class, () -> pedidoService.atualizarStatus("p1", "FINALIZADO"));
    }

    @Test
    void saidaRegistraResponsavelEDataHora() {
        pedidoComStatus("PRONTO");
        Pedido pedido = pedidoService.atualizarStatus("p1", "SAIU_PARA_ENTREGA");
        assertEquals("atendente01", pedido.getResponsavelSaida());
        assertNotNull(pedido.getDataSaida());
    }

    @Test
    void pedidoCanceladoNaoMudaDeStatus() {
        pedidoComStatus("CANCELADO");
        assertThrows(IllegalStateException.class, () -> pedidoService.atualizarStatus("p1", "EM_PREPARACAO"));
    }

    @Test
    void descontoAcimaDe20PorCentoEhRecusado() {
        pedidoComStatus("RECEBIDO");
        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("25")));
    }

    @Test
    void descontoRecalculaTotal() {
        pedidoComStatus("RECEBIDO"); // 2 x 50,00 = 100,00
        Pedido pedido = pedidoService.aplicarDesconto("p1", new BigDecimal("10"));
        assertEquals(0, new BigDecimal("90.00").compareTo(pedido.getValorTotal()));
    }

    @Test
    void descontoAposSaidaEhRecusado() {
        pedidoComStatus("SAIU_PARA_ENTREGA");
        assertThrows(IllegalStateException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("10")));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        pedidoComStatus("RECEBIDO");
        assertThrows(IllegalArgumentException.class, () -> pedidoService.cancelarPedido("p1", " "));
    }

    @Test
    void cancelamentoAposSaidaEhRecusado() {
        pedidoComStatus("RETIRADO");
        assertThrows(IllegalStateException.class,
                () -> pedidoService.cancelarPedido("p1", "Cliente desistiu"));
    }
}
