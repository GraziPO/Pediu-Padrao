package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de auditoria. E apenas incluido e consultado: o AuditoriaRepository
 * nao possui operacoes de exclusao e a API de auditoria e somente leitura.
 */
@Document(collection = "auditoria")
public class Auditoria {

    @Id
    private String id;
    private String usuario;
    private String perfil;
    private LocalDateTime dataHora;
    private String operacao;
    private String entidade;
    private String entidadeId;
    private List<Alteracao> alteracoes = new ArrayList<>();
    private String justificativa;

    /** Campo modificado com o valor anterior e o novo valor. */
    public static class Alteracao {

        private String campo;
        private String valorAnterior;
        private String valorNovo;

        public Alteracao() {
        }

        public Alteracao(String campo, String valorAnterior, String valorNovo) {
            this.campo = campo;
            this.valorAnterior = valorAnterior;
            this.valorNovo = valorNovo;
        }

        public String getCampo() {
            return campo;
        }

        public void setCampo(String campo) {
            this.campo = campo;
        }

        public String getValorAnterior() {
            return valorAnterior;
        }

        public void setValorAnterior(String valorAnterior) {
            this.valorAnterior = valorAnterior;
        }

        public String getValorNovo() {
            return valorNovo;
        }

        public void setValorNovo(String valorNovo) {
            this.valorNovo = valorNovo;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getOperacao() {
        return operacao;
    }

    public void setOperacao(String operacao) {
        this.operacao = operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(String entidadeId) {
        this.entidadeId = entidadeId;
    }

    public List<Alteracao> getAlteracoes() {
        return alteracoes;
    }

    public void setAlteracoes(List<Alteracao> alteracoes) {
        this.alteracoes = alteracoes;
    }

    public String getJustificativa() {
        return justificativa;
    }

    public void setJustificativa(String justificativa) {
        this.justificativa = justificativa;
    }
}
