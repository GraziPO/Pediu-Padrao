package com.umc.pediupatrao.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Verifica que as restricoes por perfil sao aplicadas no backend (chamadas diretas a API). */
@WebMvcTest(controllers = {PedidoController.class, UsuarioController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class SegurancaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").param("percentual", "10").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAplicaDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").param("percentual", "10").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteAplicaDesconto() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").param("percentual", "10").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void requisicaoSemTokenCsrfEhRecusada() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/desconto").param("percentual", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoCancelaPedido() throws Exception {
        mockMvc.perform(post("/api/pedidos/p1/cancelar").param("justificativa", "teste").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAdministraUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
    }

    @Test
    void cadastroDeUsuarioSemLoginNaoEhPermitido() throws Exception {
        mockMvc.perform(post("/api/usuarios").with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"x\",\"password\":\"123\",\"role\":\"ADMIN\"}"))
                .andExpect(status().is3xxRedirection()); // redireciona para o login
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() throws Exception {
        mockMvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteConsultaAuditoria() throws Exception {
        mockMvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }
}
