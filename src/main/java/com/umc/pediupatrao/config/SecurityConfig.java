package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // habilita @PreAuthorize nos services
public class SecurityConfig {

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF mantido ATIVO: formularios Thymeleaf enviam o token automaticamente
                // e as chamadas fetch enviam o header lido das meta tags do layout.html
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/dist/**", "/plugins/**", "/js/**", "/images/**").permitAll()

                        // Usuarios e perfis: somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**").hasRole("ADMIN")

                        // Auditoria: consulta somente ADMIN e GERENTE
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").hasAnyRole("ADMIN", "GERENTE")

                        // Desconto e cancelamento: somente GERENTE (inclusive em relacao ao ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar",
                                "/pedidos/*/desconto", "/pedidos/*/cancelar").hasRole("GERENTE")

                        // Registro e andamento de pedidos: ATENDENTE e GERENTE
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/pedidos/salvar", "/pedidos/*/status")
                                .hasAnyRole("ATENDENTE", "GERENTE")
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/*/status").hasAnyRole("ATENDENTE", "GERENTE")
                        .requestMatchers("/pedidos/novo").hasAnyRole("ATENDENTE", "GERENTE")

                        // Consulta de pedidos: os tres perfis
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**", "/pedidos").hasAnyRole("ADMIN", "GERENTE", "ATENDENTE")

                        // Clientes: consulta GERENTE e ATENDENTE; cadastro/alteracao somente ATENDENTE
                        .requestMatchers(HttpMethod.GET, "/api/clientes/**", "/clientes").hasAnyRole("GERENTE", "ATENDENTE")
                        .requestMatchers("/api/clientes/**", "/clientes/**").hasRole("ATENDENTE")

                        // Produtos: consulta para todos; cadastro/alteracao/exclusao por ADMIN e GERENTE
                        // (o preco do cadastro e usado no calculo do valor dos pedidos)
                        .requestMatchers(HttpMethod.GET, "/api/produtos/**", "/produtos").authenticated()
                        .requestMatchers("/api/produtos/**", "/produtos/**").hasAnyRole("ADMIN", "GERENTE")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                        .successHandler(authenticationSuccessHandler())
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect("/"); // Redireciona para a página inicial após login bem-sucedido
        };
    }
}
