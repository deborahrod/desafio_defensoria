package io.github.deborahrod.defensoria.cadastro;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cadastro.sessao")
public record PropriedadesSessao(Duration expiracao, boolean cookieSeguro, Duration retencaoEncerradas) {
}
