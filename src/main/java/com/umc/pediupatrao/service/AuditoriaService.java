package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Auditoria;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    /**
     * Registra um evento de auditoria. Somente os campos cujo valor mudou entre
     * "antes" e "depois" sao gravados. Senhas e credenciais nunca sao passadas para ca.
     */
    public void registrar(String operacao, String entidade, String entidadeId,
            Map<String, Object> antes, Map<String, Object> depois, String justificativa) {
        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(usuarioLogado());
        auditoria.setPerfil(perfilLogado());
        auditoria.setDataHora(LocalDateTime.now());
        auditoria.setOperacao(operacao);
        auditoria.setEntidade(entidade);
        auditoria.setEntidadeId(entidadeId);
        auditoria.setJustificativa(justificativa);

        for (Map.Entry<String, Object> campo : depois.entrySet()) {
            String anterior = texto(antes == null ? null : antes.get(campo.getKey()));
            String novo = texto(campo.getValue());
            if (!Objects.equals(anterior, novo)) {
                auditoria.getAlteracoes().add(new Auditoria.Alteracao(campo.getKey(), anterior, novo));
            }
        }
        auditoriaRepository.insert(auditoria);
    }

    public List<Auditoria> listar() {
        return auditoriaRepository.findAllByOrderByDataHoraDesc();
    }

    /** Username do usuario autenticado. */
    public String usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    /** Perfil do usuario autenticado (ADMIN, GERENTE ou ATENDENTE). */
    public String perfilLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse(null);
    }

    private String texto(Object valor) {
        if (valor == null || valor.toString().isBlank()) {
            return null;
        }
        return valor.toString();
    }
}
