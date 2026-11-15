package io.github.deborahrod.defensoria.integracao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração do provedor de IA. Qualquer API compatível com Chat Completions serve. */
@ConfigurationProperties("cadastro.llm")
public record PropriedadesLlm(String apiKey, String baseUrl, String model, Duration timeout, int maxOutputTokens) {

    private static final String BASE_URL_PADRAO = "https://api.mistral.ai/v1";
    private static final String MODELO_PADRAO = "mistral-small-latest";

    public PropriedadesLlm {
        apiKey = apiKey == null ? "" : apiKey.strip();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? BASE_URL_PADRAO : baseUrl.strip().replaceAll("/+$", "");
        model = model == null || model.isBlank() ? MODELO_PADRAO : model.strip();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(15) : timeout;
        maxOutputTokens = maxOutputTokens <= 0 ? 350 : maxOutputTokens;
    }

    public boolean disponivel() {
        return !apiKey.isEmpty();
    }

    // A chave não pode aparecer em log, relatório de erro ou dump de configuração.
    @Override
    public String toString() {
        return "PropriedadesLlm[baseUrl=" + baseUrl + ", model=" + model + ", timeout=" + timeout
                + ", maxOutputTokens=" + maxOutputTokens + ", apiKey=" + (disponivel() ? "***" : "(vazia)") + "]";
    }
}
