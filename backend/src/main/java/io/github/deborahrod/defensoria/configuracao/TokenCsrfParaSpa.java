package io.github.deborahrod.defensoria.configuracao;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * A interface lê o token no cookie XSRF-TOKEN e o devolve no cabeçalho X-XSRF-TOKEN.
 * O cookie é emitido em toda resposta para que a primeira mutação já possa enviá-lo.
 */
final class TokenCsrfParaSpa implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler simples = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler mascarado = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest requisicao, HttpServletResponse resposta, Supplier<CsrfToken> token) {
        mascarado.handle(requisicao, resposta, token);
        token.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest requisicao, CsrfToken token) {
        var cabecalho = requisicao.getHeader(token.getHeaderName());
        var manipulador = StringUtils.hasText(cabecalho) ? simples : mascarado;
        return manipulador.resolveCsrfTokenValue(requisicao, token);
    }
}
