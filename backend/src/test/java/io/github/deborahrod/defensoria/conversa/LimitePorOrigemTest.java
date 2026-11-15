package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.Navegador;
import io.github.deborahrod.defensoria.TesteIntegracao;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

@TestPropertySource(properties = {
    "cadastro.limites.requisicoes-por-origem-por-minuto=5",
    "cadastro.limites.confiar-em-proxy=true"
})
class LimitePorOrigemTest extends TesteIntegracao {

    // O estado do limitador vive no contexto: cada teste usa uma origem própria.
    private final String origem = "10." + UUID.randomUUID().getMostSignificantBits() % 200 + ".0." + System.nanoTime() % 250;

    private ResultActions requisitar(String endereco) throws Exception {
        return mvc.perform(get(Navegador.BASE + "/atual").header("X-Forwarded-For", endereco));
    }

    @Test
    void depoisDoTetoPorOrigemVem429ComIntervaloEEsperarLibera() throws Exception {
        for (var i = 0; i < 5; i++) {
            requisitar(origem).andExpect(status().isUnauthorized());
        }

        var resposta = requisitar(origem)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(resposta.getHeader("Retry-After"))).isBetween(1L, 61L);
        relogio.avancar(Duration.ofSeconds(62));
        requisitar(origem).andExpect(status().isUnauthorized());
    }

    @Test
    void origensDiferentesTemTetosSeparados() throws Exception {
        for (var i = 0; i < 5; i++) {
            requisitar(origem).andExpect(status().isUnauthorized());
        }
        requisitar(origem).andExpect(status().isTooManyRequests());

        requisitar(origem + "9").andExpect(status().isUnauthorized());
    }

    @Test
    void pedidoDePreflightECaminhosForaDaApiNaoContamNoTeto() throws Exception {
        for (var i = 0; i < 8; i++) {
            mvc.perform(options(Navegador.BASE + "/sessao")
                            .header("X-Forwarded-For", origem)
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isOk());
            mvc.perform(get("/actuator/health").header("X-Forwarded-For", origem)).andExpect(status().isOk());
        }

        requisitar(origem).andExpect(status().isUnauthorized());
    }
}
