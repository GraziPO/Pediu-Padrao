package com.umc.pediupatrao.entity;

import java.math.BigDecimal;

/**
 * Item do pedido (embutido no documento do pedido).
 * O preco unitario e copiado do cadastro do produto pelo servidor no registro do pedido.
 */
public class ItemPedido {

    private String produtoId;
    private String nomeProduto;
    private BigDecimal precoUnitario;
    private int quantidade;
    private BigDecimal subtotal;

    public String getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(String produtoId) {
        this.produtoId = produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public void setNomeProduto(String nomeProduto) {
        this.nomeProduto = nomeProduto;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public void setPrecoUnitario(BigDecimal precoUnitario) {
        this.precoUnitario = precoUnitario;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(int quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
