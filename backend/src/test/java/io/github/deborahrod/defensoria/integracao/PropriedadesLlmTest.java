package io.github.deborahrod.defensoria.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PropriedadesLlmTest {

    @Test
    void valoresVaziosCaemNosPadroesDaMistral() {
        var llm = new PropriedadesLlm(null, " ", "", null, 0);

        assertThat(llm.baseUrl()).isEqualTo("https://api.mistral.ai/v1");
        assertThat(llm.model()).isEqualTo("mistral-small-latest");
        assertThat(llm.timeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(llm.maxOutputTokens()).isEqualTo(350);
        assertThat(llm.disponivel()).isFalse();
    }

    @Test
    void trocarDeProvedorExigeApenasValores() {
        var llm = new PropriedadesLlm("k", "https://provedor.example/v1///", "outro-modelo", Duration.ofSeconds(8), 200);

        assertThat(llm.baseUrl()).isEqualTo("https://provedor.example/v1");
        assertThat(llm.model()).isEqualTo("outro-modelo");
        assertThat(llm.disponivel()).isTrue();
    }

    @Test
    void chaveNuncaApareceNaRepresentacaoEmTexto() {
        var llm = new PropriedadesLlm("segredo-que-nao-pode-vazar", null, null, null, 0);

        assertThat(llm.toString()).doesNotContain("segredo-que-nao-pode-vazar").contains("***");
        assertThat(new PropriedadesLlm("", null, null, null, 0).toString()).contains("(vazia)");
    }
}
