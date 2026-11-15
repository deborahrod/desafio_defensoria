package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.integracao.EventoTecnicoRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "cadastro.limites.respostas-por-sessao=6",
    "cadastro.limites.respostas-por-minuto=3"
})
class LimitesRespostasTest extends TesteIntegracao {

    @Autowired
    private EventoTecnicoRepository eventos;

    private ParticipanteApi novo() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        return participante;
    }

    @Test
    void aQuartaRespostaNoMesmoMinutoRecebe429ComIntervaloDeEspera() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA").andExpect(status().isOk());
        participante.responderValor("Ana Fictícia").andExpect(status().isOk());
        participante.responderOpcao("PULAR").andExpect(status().isOk());
        var versao = participante.versao;

        var resposta = participante.responderValor("20/05/1990")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"))
                .andExpect(jsonPath("$.mensagem").value(containsString("Aguarde")))
                .andReturn().getResponse();

        var espera = Long.parseLong(resposta.getHeader("Retry-After"));
        assertThat(espera).isBetween(1L, 61L);
        // A resposta recusada não foi processada: o cadastro continua onde estava.
        participante.navegador.get("/atual")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(versao))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("nascimento"));

        relogio.avancar(Duration.ofSeconds(espera));
        participante.responderValor("20/05/1990").andExpect(status().isOk());
    }

    @Test
    void respostaRecusadaPeloLimiteNaoConsomeOLimiteDaSessao() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");
        participante.responderValor("Ana Fictícia");
        participante.responderOpcao("PULAR");
        for (var i = 0; i < 5; i++) {
            participante.responderValor("20/05/1990").andExpect(status().isTooManyRequests());
        }
        relogio.avancar(Duration.ofSeconds(61));

        // Três respostas novas cabem, mesmo depois de cinco tentativas recusadas.
        participante.responderValor("20/05/1990").andExpect(status().isOk());
        participante.responderOpcao("NAO_POSSUI").andExpect(status().isOk());
        participante.responderOpcao("INDISPONIVEL").andExpect(status().isOk());
    }

    @Test
    void depoisDoLimiteDaSessaoEsperarNaoAdiantaEAOutraSessaoNaoEAfetada() throws Exception {
        var a = novo();
        a.responderOpcao("PROPRIA_PESSOA");
        a.responderValor("Ana Fictícia");
        a.responderOpcao("PULAR");
        relogio.avancar(Duration.ofSeconds(61));
        a.responderValor("20/05/1990");
        a.responderOpcao("NAO_POSSUI");
        a.responderOpcao("INDISPONIVEL");
        relogio.avancar(Duration.ofSeconds(61));

        var resposta = a.responderOpcao("NAO_SEI")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem").value(containsString("limite de respostas desta sessão")))
                .andReturn().getResponse();

        assertThat(resposta.getHeader("Retry-After")).isNull();
        var b = novo();
        b.responderOpcao("PROPRIA_PESSOA").andExpect(status().isOk());
        assertThat(eventos.findAll()).extracting("status").contains("RESPOSTAS_POR_SESSAO");
    }

    @Test
    void recusaPeloLimiteFicaRegistradaComoEventoTecnicoSemConteudo() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");
        participante.responderValor("Ana Fictícia");
        participante.responderOpcao("PULAR");

        participante.responderValor("20/05/1990").andExpect(status().isTooManyRequests());

        assertThat(eventos.findAll()).singleElement().satisfies(evento -> {
            assertThat(evento.getTipo()).isEqualTo("LIMITE");
            assertThat(evento.getStatus()).isEqualTo("RESPOSTAS_POR_MINUTO");
            assertThat(evento.getModelo()).isNull();
        });
    }

    @Test
    void correcaoTambemContaParaOLimiteDeRespostas() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");
        participante.responderValor("Ana Fictícia");
        participante.responderOpcao("PULAR");

        participante.corrigir("nome", "\"Bia Fictícia\"", "null").andExpect(status().isTooManyRequests());
    }

    @Test
    void mensagemDeMilCaracteresChegaAoDominioEDeMilEUmaEBarradaNaEntrada() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");

        participante.responderValor("a".repeat(1000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RESPOSTA_INVALIDA"));
        participante.responder(UUID.randomUUID(), participante.versao, "\"" + "a".repeat(1001) + "\"", "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
                .andExpect(jsonPath("$.campo").value("valor"));
        participante.responderOpcao("NAO_EXISTE").andExpect(status().isBadRequest());
        // Foram contadas três respostas; a de 1001 caracteres caiu na validação de entrada e não conta.
        participante.responderValor("Ana Fictícia").andExpect(status().isTooManyRequests());
    }

    @Test
    void confirmarNaoEBloqueadoPeloLimiteDeRespostas() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");

        // Sem revisão não há o que confirmar; o ponto é que a rota não passa pelo limitador de respostas.
        participante.confirmar().andExpect(status().isConflict());
        participante.confirmar().andExpect(status().isConflict());
        participante.confirmar().andExpect(status().isConflict());
        participante.confirmar().andExpect(status().isConflict()).andExpect(header().doesNotExist("Retry-After"));
    }
}
