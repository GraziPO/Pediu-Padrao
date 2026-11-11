package com.umc.pediupatrao.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Acesso ao dashboard por perfil e respostas de erro da API (/api/dashboard). */
@WebMvcTest(controllers = {DashboardController.class, DashboardPageController.class})
@Import(SecurityConfig.class)
class DashboardSegurancaTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void gerenteConsultaOResumo() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaOResumo() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoConsultaAApi() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente", roles = "ATENDENTE")
    void atendenteNaoAbreATela() throws Exception {
        mockMvc.perform(get("/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void usuarioNaoLogadoEhRedirecionadoParaOLogin() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void dataEmFormatoInvalidoRetorna400() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").param("dataInicio", "09/11/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "gerente", roles = "GERENTE")
    void filtroRecusadoPeloServicoRetorna400ComAMensagem() throws Exception {
        when(dashboardService.criarFiltro(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("A data inicial deve ser igual ou anterior à data final."));
        mockMvc.perform(get("/api/dashboard/resumo").param("dataInicio", "2026-11-10").param("dataFim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("data inicial")));
    }
}
