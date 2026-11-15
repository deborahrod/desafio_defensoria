package io.github.deborahrod.defensoria.configuracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.deborahrod.defensoria.conversa.LimitadorRequisicoes;
import io.github.deborahrod.defensoria.conversa.PropriedadesLimites;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Teto de requisições por origem, somado ao limite por sessão. O endereço não vai para log nem banco. */
@Component
class FiltroLimitePorOrigem extends OncePerRequestFilter {

    private final LimitadorRequisicoes limitador;
    private final PropriedadesLimites limites;
    private final ObjectMapper json;

    FiltroLimitePorOrigem(LimitadorRequisicoes limitador, PropriedadesLimites limites, ObjectMapper json) {
        this.limitador = limitador;
        this.limites = limites;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        return !requisicao.getRequestURI().startsWith("/api/")
                || HttpMethod.OPTIONS.matches(requisicao.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        var espera = limitador.permitirOrigem(chaveDeOrigem(requisicao, limites.confiarEmProxy()));
        if (espera > 0) {
            resposta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            resposta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(espera));
            resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
            json.writeValue(resposta.getWriter(), new ErroResposta(
                    "LIMITE_EXCEDIDO", "Muitas requisições deste acesso. Aguarde " + espera + " segundos."));
            return;
        }
        cadeia.doFilter(requisicao, resposta);
    }

    static String chaveDeOrigem(HttpServletRequest requisicao, boolean confiarEmProxy) {
        var encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (confiarEmProxy && encaminhado != null && !encaminhado.isBlank()) {
            var primeiro = encaminhado.split(",")[0].strip();
            return primeiro.substring(0, Math.min(primeiro.length(), 64));
        }
        return requisicao.getRemoteAddr();
    }
}
