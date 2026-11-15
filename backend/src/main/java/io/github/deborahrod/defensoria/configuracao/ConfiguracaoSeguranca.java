package io.github.deborahrod.defensoria.configuracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.deborahrod.defensoria.cadastro.PropriedadesSessao;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class ConfiguracaoSeguranca {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, PropriedadesSessao sessao, ObjectMapper json) throws Exception {
        var tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokens.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(sessao.cookieSeguro()));

        return http
                .authorizeHttpRequests(regras -> regras.anyRequest().permitAll())
                .sessionManagement(sessoes -> sessoes.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(tokens).csrfTokenRequestHandler(new TokenCsrfParaSpa()))
                .cors(Customizer.withDefaults())
                .exceptionHandling(erros -> erros.accessDeniedHandler(requisicaoRecusada(json)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    // Sem origens configuradas, só a mesma origem é aceita (nginx ou proxy do Vite).
    @Bean
    CorsConfigurationSource corsConfigurationSource(PropriedadesCors cors) {
        var regras = new CorsConfiguration();
        regras.setAllowedOrigins(cors.origens());
        regras.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
        regras.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        regras.setAllowCredentials(true);

        var fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", regras);
        return fonte;
    }

    private AccessDeniedHandler requisicaoRecusada(ObjectMapper json) {
        return (requisicao, resposta, erro) -> {
            resposta.setStatus(HttpStatus.FORBIDDEN.value());
            resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
            json.writeValue(resposta.getWriter(), new ErroResposta(
                    "REQUISICAO_RECUSADA",
                    "Não foi possível validar a requisição. Recarregue a página e tente novamente."));
        };
    }
}
