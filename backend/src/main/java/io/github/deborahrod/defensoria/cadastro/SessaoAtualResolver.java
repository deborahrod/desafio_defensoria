package io.github.deborahrod.defensoria.cadastro;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.WebUtils;

/** Entrega ao controlador a sessão do cookie. Sem cookie válido, a rota não é atendida. */
@Component
public class SessaoAtualResolver implements HandlerMethodArgumentResolver {

    private final ServicoSessao sessoes;

    public SessaoAtualResolver(ServicoSessao sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public boolean supportsParameter(MethodParameter parametro) {
        return parametro.getParameterType().equals(SessaoCadastro.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parametro,
            ModelAndViewContainer modelo,
            NativeWebRequest requisicao,
            WebDataBinderFactory binderFactory) {
        var http = requisicao.getNativeRequest(HttpServletRequest.class);
        var cookie = http == null ? null : WebUtils.getCookie(http, CookieSessao.NOME);
        if (cookie == null || cookie.getValue().isBlank()) {
            throw new SessaoInexistenteException();
        }
        return sessoes.autenticar(cookie.getValue());
    }
}
