package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    private static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private AuditoriaService auditoriaService;

    /**
     * Registra o pedido (RECEBIDO). Da requisicao sao usados apenas cliente, produtos e quantidades;
     * precos, total, status, responsavel e data/hora de entrada sao definidos aqui no servidor.
     */
    public Pedido criarPedido(Pedido pedido) {
        if (pedido.getClienteId() == null || !clienteRepository.existsById(pedido.getClienteId())) {
            throw new IllegalArgumentException("Cliente não encontrado.");
        }
        if (pedido.getItens() == null || pedido.getItens().isEmpty()) {
            throw new IllegalArgumentException("O pedido deve ter ao menos um item.");
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedido recebido : pedido.getItens()) {
            if (recebido.getQuantidade() < 1) {
                throw new IllegalArgumentException("A quantidade de cada item deve ser maior que zero.");
            }
            Produto produto = produtoRepository.findById(String.valueOf(recebido.getProdutoId()))
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (Boolean.FALSE.equals(produto.getAtivo()) || produto.getPreco() == null) {
                throw new IllegalArgumentException("Produto indisponível: " + produto.getNome());
            }
            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setPrecoUnitario(BigDecimal.valueOf(produto.getPreco()));
            item.setQuantidade(recebido.getQuantidade());
            itens.add(item);
        }

        pedido.setId(null); // sempre um novo pedido
        pedido.setItens(itens);
        pedido.setPercentualDesconto(BigDecimal.ZERO);
        pedido.setStatus(StatusPedido.RECEBIDO.name());
        pedido.setRegistradoPor(auditoriaService.usuarioLogado());
        pedido.setDataEntrada(LocalDateTime.now());
        pedido.setResponsavelSaida(null);
        pedido.setDataSaida(null);
        pedido.setJustificativaCancelamento(null);
        calcularValores(pedido);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("INCLUSÃO DE PEDIDO", "Pedido", salvo.getId(), null, campos(salvo), null);
        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = buscar(id);
        StatusPedido atual = converter(pedido.getStatus());
        StatusPedido novo = converter(status);

        if (atual == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Pedido cancelado não pode ter o status alterado.");
        }
        if (novo == StatusPedido.CANCELADO) {
            throw new IllegalStateException("Para cancelar, use a operação de cancelamento (exclusiva do GERENTE).");
        }
        if (!atual.podeIrPara(novo)) {
            throw new IllegalStateException("Transição inválida: " + atual + " -> " + novo
                    + ". Permitido: " + atual.proximos());
        }

        Map<String, Object> antes = campos(pedido);
        pedido.setStatus(novo.name());
        if (novo == StatusPedido.SAIU_PARA_ENTREGA || novo == StatusPedido.RETIRADO) {
            // Saida: registra obrigatoriamente data/hora e responsavel
            pedido.setResponsavelSaida(auditoriaService.usuarioLogado());
            pedido.setDataSaida(LocalDateTime.now());
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("ALTERAÇÃO DE STATUS DO PEDIDO", "Pedido", id, antes, campos(salvo), null);
        return salvo;
    }

    /** Desconto percentual (ate 20%), exclusivo do GERENTE e somente antes da saida. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null || percentual.compareTo(BigDecimal.ZERO) <= 0
                || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new IllegalArgumentException("O desconto deve ser maior que 0% e no máximo 20%.");
        }
        Pedido pedido = buscar(id);
        if (!converter(pedido.getStatus()).antesDaSaida()) {
            throw new IllegalStateException("Desconto só é permitido antes da saída para entrega ou retirada.");
        }

        Map<String, Object> antes = campos(pedido);
        pedido.setPercentualDesconto(percentual); // um novo desconto substitui o anterior
        calcularValores(pedido);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("DESCONTO EM PEDIDO", "Pedido", id, antes, campos(salvo), null);
        return salvo;
    }

    /** Cancelamento com justificativa, exclusivo do GERENTE e somente antes da saida. */
    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelarPedido(String id, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa do cancelamento é obrigatória.");
        }
        Pedido pedido = buscar(id);
        if (!converter(pedido.getStatus()).antesDaSaida()) {
            throw new IllegalStateException("Cancelamento só é permitido antes da saída para entrega ou retirada.");
        }

        Map<String, Object> antes = campos(pedido);
        // Os demais dados sao preservados como historico
        pedido.setStatus(StatusPedido.CANCELADO.name());
        pedido.setJustificativaCancelamento(justificativa);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar("CANCELAMENTO DE PEDIDO", "Pedido", id, antes, campos(salvo), justificativa);
        return salvo;
    }

    private Pedido buscar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido não encontrado."));
    }

    private StatusPedido converter(String status) {
        try {
            return StatusPedido.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Status inválido: " + status);
        }
    }

    /** Valor total = soma dos itens (preco x quantidade) - desconto. */
    private void calcularValores(Pedido pedido) {
        BigDecimal soma = BigDecimal.ZERO;
        for (ItemPedido item : pedido.getItens()) {
            item.setSubtotal(item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade())));
            soma = soma.add(item.getSubtotal());
        }
        BigDecimal desconto = soma.multiply(pedido.getPercentualDesconto())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(soma.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
    }

    /** Campos do pedido acompanhados pela auditoria. */
    private Map<String, Object> campos(Pedido p) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("clienteId", p.getClienteId());
        StringBuilder itens = new StringBuilder();
        for (ItemPedido item : p.getItens()) {
            itens.append(item.getQuantidade()).append("x ").append(item.getNomeProduto()).append("; ");
        }
        campos.put("itens", itens.toString().trim());
        campos.put("percentualDesconto", p.getPercentualDesconto());
        campos.put("valorDesconto", p.getValorDesconto());
        campos.put("valorTotal", p.getValorTotal());
        campos.put("status", p.getStatus());
        campos.put("registradoPor", p.getRegistradoPor());
        campos.put("dataEntrada", p.getDataEntrada());
        campos.put("responsavelSaida", p.getResponsavelSaida());
        campos.put("dataSaida", p.getDataSaida());
        return campos;
    }
}
