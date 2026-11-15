package io.github.deborahrod.defensoria.cadastro;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieSessao {

    public static final String NOME = "sessao_cadastro";

    private final PropriedadesSessao propriedades;

    public CookieSessao(PropriedadesSessao propriedades) {
        this.propriedades = propriedades;
    }

    public ResponseCookie criar(String segredo) {
        return base(segredo).build();
    }

    public ResponseCookie apagar() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOME, valor)
                .httpOnly(true)
                .secure(propriedades.cookieSeguro())
                .sameSite("Strict")
                .path("/api");
    }
}
