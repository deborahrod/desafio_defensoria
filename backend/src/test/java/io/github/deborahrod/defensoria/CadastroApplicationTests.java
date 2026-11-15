package io.github.deborahrod.defensoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

class CadastroApplicationTests extends TesteIntegracao {

    @Autowired
    private TestRestTemplate http;

    @Test
    void aplicacaoSobeEInformaSaude() {
        var resposta = http.getForEntity("/actuator/health", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("UP");
    }
}
