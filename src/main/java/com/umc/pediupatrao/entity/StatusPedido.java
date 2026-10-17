package com.umc.pediupatrao.entity;

import java.util.List;

/**
 * Fluxo obrigatorio do pedido:
 * RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA ou RETIRADO -> FINALIZADO.
 * CANCELADO e excepcional: so pelo GERENTE e antes da saida (ver PedidoService.cancelarPedido).
 */
public enum StatusPedido {
    RECEBIDO,
    EM_PREPARACAO,
    PRONTO,
    SAIU_PARA_ENTREGA,
    RETIRADO,
    FINALIZADO,
    CANCELADO;

    /** Proximos status permitidos no fluxo normal. */
    public List<StatusPedido> proximos() {
        return switch (this) {
            case RECEBIDO -> List.of(EM_PREPARACAO);
            case EM_PREPARACAO -> List.of(PRONTO);
            case PRONTO -> List.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> List.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> List.of();
        };
    }

    public boolean podeIrPara(StatusPedido destino) {
        return proximos().contains(destino);
    }

    /** Desconto e cancelamento so sao permitidos antes da saida para entrega ou retirada. */
    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }
}
