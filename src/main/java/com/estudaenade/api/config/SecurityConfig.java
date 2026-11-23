package com.estudaenade.api.config;

import com.estudaenade.api.domain.enums.Papel;
import com.estudaenade.api.infra.security.JwtAuthenticationFilter;
import com.estudaenade.api.infra.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Value("${cors.allowed-origins}")
    private List<String> origensPermitidas;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sem token válido → 401 (o cliente usa o 401 para renovar a sessão; o padrão do Spring seria 403)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(acessoNegado()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // Documentação: Swagger UI e o arquivo de contrato OpenAPI
                        .requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/swagger-config", "/openapi/**").permitAll()
                        .requestMatchers("/api/v1/professor/**").hasRole("PROFESSOR")
                        .requestMatchers("/api/v1/aluno/**").hasRole("ALUNO")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Rota de outro papel → 403 no formato de erro da API. O professor com cadastro pendente recebe
     * CADASTRO_PENDENTE, para o cliente mostrar que ele aguarda aprovação.
     */
    private static AccessDeniedHandler acessoNegado() {
        return (request, response, ex) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean pendente = auth != null && auth.getPrincipal() instanceof UsuarioLogado u
                    && u.papel() == Papel.PROFESSOR && !u.aprovado();
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(pendente
                    ? "{\"codigo\":\"CADASTRO_PENDENTE\",\"mensagem\":\"Seu cadastro de professor aguarda aprovação.\"}"
                    : "{\"codigo\":\"ACESSO_NEGADO\",\"mensagem\":\"Você não tem acesso a este recurso.\"}");
        };
    }

    /**
     * O login com senha é feito pela API ({@code /auth/entrar}, que emite o JWT próprio), não pelo form login do
     * Spring. Este bean só evita o usuário padrão (com senha no log) que o Spring criaria.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    /** BCrypt, com o prefixo do algoritmo no hash ({@code {bcrypt}...}) para permitir trocá-lo no futuro. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origensPermitidas.stream().map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
