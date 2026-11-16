package io.github.deborahrod.defensoria.integracao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração da consulta de CEP. As URLs são configuráveis para os testes apontarem para um servidor simulado. */
@ConfigurationProperties("cadastro.cep")
public record PropriedadesCep(
        Duration timeout, String viacepUrl, String brasilapiUrl, int cacheMaximo, Duration cacheValidade) {

    public PropriedadesCep {
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(3) : timeout;
        viacepUrl = limpar(viacepUrl, "https://viacep.com.br/ws");
        brasilapiUrl = limpar(brasilapiUrl, "https://brasilapi.com.br/api/cep/v2");
        cacheMaximo = cacheMaximo <= 0 ? 500 : cacheMaximo;
        cacheValidade = cacheValidade == null || cacheValidade.isZero() || cacheValidade.isNegative()
                ? Duration.ofHours(6)
                : cacheValidade;
    }

    private static String limpar(String url, String padrao) {
        return url == null || url.isBlank() ? padrao : url.strip().replaceAll("/+$", "");
    }
}
