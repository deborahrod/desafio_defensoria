package io.github.deborahrod.defensoria;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Simula um navegador: guarda os cookies recebidos e devolve o token CSRF nas mutações. */
public class Navegador {

    public static final String BASE = "/api/v1/cadastro";

    private final MockMvc mvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();

    Navegador(MockMvc mvc) {
        this.mvc = mvc;
    }

    public ResultActions get(String caminho) throws Exception {
        return executar(HttpMethod.GET, caminho, null);
    }

    public ResultActions post(String caminho) throws Exception {
        return executar(HttpMethod.POST, caminho, null);
    }

    public ResultActions post(String caminho, String json) throws Exception {
        return executar(HttpMethod.POST, caminho, json);
    }

    public ResultActions patch(String caminho, String json) throws Exception {
        return executar(HttpMethod.PATCH, caminho, json);
    }

    public ResultActions delete(String caminho) throws Exception {
        return executar(HttpMethod.DELETE, caminho, null);
    }

    /** Inicia uma sessão de cadastro já com o token CSRF em mãos. */
    public ResultActions iniciarSessao() throws Exception {
        get("/atual");
        return post("/sessao");
    }

    public Cookie cookie(String nome) {
        return cookies.get(nome);
    }

    public void definirCookie(String nome, String valor) {
        cookies.put(nome, new Cookie(nome, valor));
    }

    public void esquecerCookie(String nome) {
        cookies.remove(nome);
    }

    private ResultActions executar(HttpMethod metodo, String caminho, String json) throws Exception {
        MockHttpServletRequestBuilder requisicao = MockMvcRequestBuilders.request(metodo, BASE + caminho);
        if (json != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (!cookies.isEmpty()) {
            requisicao.cookie(cookies.values().toArray(Cookie[]::new));
        }
        var csrf = cookies.get("XSRF-TOKEN");
        if (metodo != HttpMethod.GET && csrf != null) {
            requisicao.header("X-XSRF-TOKEN", csrf.getValue());
        }
        var resultado = mvc.perform(requisicao);
        for (var recebido : resultado.andReturn().getResponse().getCookies()) {
            if (recebido.getMaxAge() == 0) {
                cookies.remove(recebido.getName());
            } else {
                cookies.put(recebido.getName(), new Cookie(recebido.getName(), recebido.getValue()));
            }
        }
        return resultado;
    }
}
