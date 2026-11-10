package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDto.Comparativo;
import com.umc.pediupatrao.dto.DashboardDto.PedidoRecente;
import com.umc.pediupatrao.dto.DashboardDto.ProdutoVendido;
import com.umc.pediupatrao.dto.DashboardDto.Resumo;
import com.umc.pediupatrao.dto.DashboardDto.StatusQuantidade;
import com.umc.pediupatrao.dto.DashboardDto.VendaDia;
import com.umc.pediupatrao.dto.DashboardDto.VendaHora;
import com.umc.pediupatrao.dto.DashboardDto.VendaTipo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Indicadores do dashboard administrativo.
 *
 * Regras de calculo:
 * - Os pedidos sao selecionados pela data de entrada (dataEntrada), com dataInicio e dataFim inclusivos.
 * - Pedidos CANCELADOS entram na contagem total e na taxa de cancelamento, mas nao no faturamento,
 *   no ticket medio, nos graficos de vendas nem no ranking de produtos.
 * - Sem filtro de tipo, o valor de um pedido e o valorTotal (ja com desconto).
 *   Com filtro de tipo, contam apenas os pedidos com algum item daquele tipo, e o valor considerado
 *   e a soma dos subtotais desses itens (sem rateio do desconto).
 */
@Service
public class DashboardService {

    public static final int PERIODO_PADRAO_DIAS = 7;
    public static final int PERIODO_MAXIMO_DIAS = 366;
    public static final int LIMITE_MAXIMO = 50;
    private static final String SEM_TIPO = "Sem tipo";

    private final MongoTemplate mongoTemplate;
    private final ProdutoRepository produtoRepository;
    private final ClienteRepository clienteRepository;

    public DashboardService(MongoTemplate mongoTemplate, ProdutoRepository produtoRepository,
            ClienteRepository clienteRepository) {
        this.mongoTemplate = mongoTemplate;
        this.produtoRepository = produtoRepository;
        this.clienteRepository = clienteRepository;
    }

    /** Filtros de consulta (RF05), ja validados. */
    public record Filtro(LocalDate inicio, LocalDate fim, StatusPedido status, String tipo) {

        public long dias() {
            return ChronoUnit.DAYS.between(inicio, fim) + 1;
        }

        Filtro periodoAnterior() {
            return new Filtro(inicio.minusDays(dias()), inicio.minusDays(1), status, tipo);
        }
    }

    /**
     * Valida e completa os filtros. Sem datas, usa os ultimos 7 dias ate hoje.
     *
     * @throws IllegalArgumentException periodo invertido, maior que 366 dias ou status inexistente
     */
    public Filtro criarFiltro(LocalDate dataInicio, LocalDate dataFim, String status, String tipo, LocalDate hoje) {
        LocalDate fim = dataFim != null ? dataFim : hoje;
        LocalDate inicio = dataInicio != null ? dataInicio : fim.minusDays(PERIODO_PADRAO_DIAS - 1);
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial deve ser igual ou anterior à data final.");
        }
        if (ChronoUnit.DAYS.between(inicio, fim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("O período consultado não pode passar de " + PERIODO_MAXIMO_DIAS + " dias.");
        }
        StatusPedido statusFiltro = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFiltro = StatusPedido.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Status inválido: " + status + ".");
            }
        }
        String tipoFiltro = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        return new Filtro(inicio, fim, statusFiltro, tipoFiltro);
    }

    public static int validarLimite(Integer limite, int padrao) {
        int valor = limite == null ? padrao : limite;
        if (valor < 1 || valor > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".");
        }
        return valor;
    }

    // ========================
    // RF01 - Indicadores gerais
    // ========================
    public Resumo resumo(Filtro filtro) {
        Map<String, String> tipos = tiposPorProduto();
        Totais atual = totais(buscarPedidos(filtro, tipos), filtro, tipos);
        Filtro anterior = filtro.periodoAnterior();
        Totais antes = totais(buscarPedidos(anterior, tipos), anterior, tipos);

        BigDecimal taxa = atual.total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(atual.cancelados * 100).divide(BigDecimal.valueOf(atual.total), 2, RoundingMode.HALF_UP);

        return new Resumo(filtro.inicio(), filtro.fim(), atual.faturamento, atual.total, atual.cancelados,
                atual.ticketMedio(), taxa,
                new Comparativo(anterior.inicio(), anterior.fim(), antes.faturamento, antes.total, antes.ticketMedio()));
    }

    // ========================
    // RF02 - Analise de vendas
    // ========================
    public List<VendaDia> vendasPorDia(Filtro filtro) {
        Map<String, String> tipos = tiposPorProduto();
        Map<LocalDate, long[]> pedidos = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> valores = new LinkedHashMap<>();
        for (LocalDate d = filtro.inicio(); !d.isAfter(filtro.fim()); d = d.plusDays(1)) {
            pedidos.put(d, new long[1]);
            valores.put(d, BigDecimal.ZERO);
        }
        for (Pedido p : naoCancelados(buscarPedidos(filtro, tipos))) {
            LocalDate dia = p.getDataEntrada().toLocalDate();
            if (!pedidos.containsKey(dia)) {
                continue;
            }
            pedidos.get(dia)[0]++;
            valores.merge(dia, valorDoPedido(p, filtro, tipos), BigDecimal::add);
        }
        List<VendaDia> resultado = new ArrayList<>();
        pedidos.forEach((dia, qtd) -> resultado.add(new VendaDia(dia, qtd[0], dinheiro(valores.get(dia)))));
        return resultado;
    }

    public List<VendaHora> vendasPorHora(Filtro filtro) {
        Map<String, String> tipos = tiposPorProduto();
        long[] pedidos = new long[24];
        BigDecimal[] valores = new BigDecimal[24];
        java.util.Arrays.fill(valores, BigDecimal.ZERO);
        for (Pedido p : naoCancelados(buscarPedidos(filtro, tipos))) {
            int hora = p.getDataEntrada().getHour();
            pedidos[hora]++;
            valores[hora] = valores[hora].add(valorDoPedido(p, filtro, tipos));
        }
        List<VendaHora> resultado = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            resultado.add(new VendaHora(h, pedidos[h], dinheiro(valores[h])));
        }
        return resultado;
    }

    // ========================
    // RF03 - Situacao dos pedidos
    // ========================
    public List<StatusQuantidade> statusPedidos(Filtro filtro) {
        List<Pedido> pedidos = buscarPedidos(filtro, tiposPorProduto());
        Map<String, Long> contagem = pedidos.stream()
                .collect(Collectors.groupingBy(p -> Objects.toString(p.getStatus(), ""), Collectors.counting()));
        long total = pedidos.size();
        List<StatusQuantidade> resultado = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            long qtd = contagem.getOrDefault(s.name(), 0L);
            BigDecimal pct = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(qtd * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
            resultado.add(new StatusQuantidade(s.name(), qtd, pct));
        }
        return resultado;
    }

    public List<PedidoRecente> pedidosRecentes(Filtro filtro, int limite) {
        List<Pedido> pedidos = buscarPedidos(filtro, tiposPorProduto()).stream()
                .sorted(Comparator.comparing(Pedido::getDataEntrada).reversed())
                .limit(limite)
                .toList();
        Set<String> ids = pedidos.stream().map(Pedido::getClienteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> nomes = new HashMap<>();
        for (Cliente c : clienteRepository.findAllById(ids)) {
            nomes.put(c.getId(), c.getNome());
        }
        return pedidos.stream()
                .map(p -> new PedidoRecente(p.getId(), p.getDataEntrada(), p.getClienteId(),
                        nomes.getOrDefault(p.getClienteId(), "Cliente não encontrado"),
                        p.getItens() == null ? 0 : p.getItens().stream().mapToInt(ItemPedido::getQuantidade).sum(),
                        dinheiro(p.getValorTotal()), p.getStatus()))
                .toList();
    }

    // ========================
    // RF04 - Produtos mais vendidos
    // ========================
    public List<ProdutoVendido> produtosMaisVendidos(Filtro filtro, int limite, String ordenarPor) {
        boolean porFaturamento = "faturamento".equalsIgnoreCase(ordenarPor);
        if (ordenarPor != null && !ordenarPor.isBlank() && !porFaturamento && !"quantidade".equalsIgnoreCase(ordenarPor)) {
            throw new IllegalArgumentException("ordenarPor deve ser 'quantidade' ou 'faturamento'.");
        }
        Map<String, String> tipos = tiposPorProduto();
        Map<String, ProdutoVendido> ranking = new LinkedHashMap<>();
        for (Pedido p : naoCancelados(buscarPedidos(filtro, tipos))) {
            for (ItemPedido item : itensConsiderados(p, filtro, tipos)) {
                String chave = item.getProdutoId() != null ? item.getProdutoId() : item.getNomeProduto();
                ProdutoVendido atual = ranking.get(chave);
                long qtd = item.getQuantidade() + (atual == null ? 0 : atual.quantidade());
                BigDecimal valor = subtotal(item).add(atual == null ? BigDecimal.ZERO : atual.faturamento());
                ranking.put(chave, new ProdutoVendido(item.getProdutoId(), item.getNomeProduto(),
                        tipoDoItem(item, tipos), qtd, dinheiro(valor)));
            }
        }
        Comparator<ProdutoVendido> ordem = porFaturamento
                ? Comparator.comparing(ProdutoVendido::faturamento).thenComparing(ProdutoVendido::quantidade)
                : Comparator.comparing(ProdutoVendido::quantidade).thenComparing(ProdutoVendido::faturamento);
        return ranking.values().stream()
                .sorted(ordem.reversed().thenComparing(ProdutoVendido::nome, Comparator.nullsLast(String::compareTo)))
                .limit(limite)
                .toList();
    }

    public List<VendaTipo> vendasPorTipo(Filtro filtro) {
        Map<String, String> tipos = tiposPorProduto();
        Map<String, long[]> quantidades = new HashMap<>();
        Map<String, BigDecimal> valores = new HashMap<>();
        for (Pedido p : naoCancelados(buscarPedidos(filtro, tipos))) {
            for (ItemPedido item : itensConsiderados(p, filtro, tipos)) {
                String tipo = tipoDoItem(item, tipos);
                quantidades.computeIfAbsent(tipo, t -> new long[1])[0] += item.getQuantidade();
                valores.merge(tipo, subtotal(item), BigDecimal::add);
            }
        }
        return valores.entrySet().stream()
                .map(e -> new VendaTipo(e.getKey(), quantidades.get(e.getKey())[0], dinheiro(e.getValue())))
                .sorted(Comparator.comparing(VendaTipo::faturamento).reversed())
                .toList();
    }

    /** Tipos cadastrados nos produtos, para o filtro da tela. */
    public List<String> tiposDeProduto() {
        Set<String> tipos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Produto p : produtoRepository.findAll()) {
            if (p.getTipo() != null && !p.getTipo().isBlank()) {
                tipos.add(p.getTipo().trim());
            }
        }
        return new ArrayList<>(tipos);
    }

    // ========================
    // Apoio
    // ========================

    /** Busca no banco os pedidos do periodo (e do status, se informado); o filtro de tipo e aplicado em memoria. */
    List<Pedido> buscarPedidos(Filtro filtro, Map<String, String> tipos) {
        Criteria criteria = Criteria.where("dataEntrada")
                .gte(filtro.inicio().atStartOfDay())
                .lt(filtro.fim().plusDays(1).atStartOfDay());
        if (filtro.status() != null) {
            criteria = criteria.and("status").is(filtro.status().name());
        }
        List<Pedido> pedidos = mongoTemplate.find(new Query(criteria), Pedido.class);
        if (filtro.tipo() == null) {
            return pedidos;
        }
        return pedidos.stream().filter(p -> !itensConsiderados(p, filtro, tipos).isEmpty()).toList();
    }

    private Map<String, String> tiposPorProduto() {
        Map<String, String> tipos = new HashMap<>();
        for (Produto p : produtoRepository.findAll()) {
            if (p.getId() != null && p.getTipo() != null && !p.getTipo().isBlank()) {
                tipos.put(p.getId(), p.getTipo().trim());
            }
        }
        return tipos;
    }

    private static String tipoDoItem(ItemPedido item, Map<String, String> tipos) {
        return tipos.getOrDefault(item.getProdutoId(), SEM_TIPO);
    }

    private static List<ItemPedido> itensConsiderados(Pedido p, Filtro filtro, Map<String, String> tipos) {
        if (p.getItens() == null) {
            return List.of();
        }
        if (filtro.tipo() == null) {
            return p.getItens();
        }
        return p.getItens().stream().filter(i -> filtro.tipo().equalsIgnoreCase(tipoDoItem(i, tipos))).toList();
    }

    private static BigDecimal valorDoPedido(Pedido p, Filtro filtro, Map<String, String> tipos) {
        if (filtro.tipo() == null) {
            return p.getValorTotal() == null ? BigDecimal.ZERO : p.getValorTotal();
        }
        return itensConsiderados(p, filtro, tipos).stream().map(DashboardService::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal subtotal(ItemPedido item) {
        if (item.getSubtotal() != null) {
            return item.getSubtotal();
        }
        if (item.getPrecoUnitario() == null) {
            return BigDecimal.ZERO;
        }
        return item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()));
    }

    private static List<Pedido> naoCancelados(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> !StatusPedido.CANCELADO.name().equals(p.getStatus())).toList();
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }

    private Totais totais(List<Pedido> pedidos, Filtro filtro, Map<String, String> tipos) {
        Totais t = new Totais();
        t.total = pedidos.size();
        for (Pedido p : pedidos) {
            if (StatusPedido.CANCELADO.name().equals(p.getStatus())) {
                t.cancelados++;
            } else {
                t.faturamento = t.faturamento.add(valorDoPedido(p, filtro, tipos));
            }
        }
        t.faturamento = dinheiro(t.faturamento);
        return t;
    }

    private static final class Totais {
        long total;
        long cancelados;
        BigDecimal faturamento = BigDecimal.ZERO;

        BigDecimal ticketMedio() {
            long validos = total - cancelados;
            return validos == 0 ? dinheiro(BigDecimal.ZERO)
                    : faturamento.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP);
        }
    }
}
