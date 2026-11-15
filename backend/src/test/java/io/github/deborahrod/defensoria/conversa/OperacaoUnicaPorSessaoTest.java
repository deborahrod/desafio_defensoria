package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.opcao;
import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Candidato;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "cadastro.llm.api-key=chave-de-teste")
class OperacaoUnicaPorSessaoTest extends TesteIntegracao {

    private static final List<RespostaEntrada> ATE_A_IA = List.of(
            opcao("PROPRIA_PESSOA"), valor("Ana Fictícia"), opcao("PULAR"), opcao("NAO_SEI"),
            opcao("NAO_POSSUI"), opcao("INDISPONIVEL"), opcao("USAR_IA"));

    @Test
    void enquantoUmaInferenciaEstaEmAndamentoOutraRespostaDaSessaoEhRecusadaSemChamarOModelo() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar();
        participante.responderTudo(ATE_A_IA);
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        interpretador.bloquearAte(entrou, liberar, new ResultadoInterpretacao(
                Operacao.PROPOR_VALORES, List.of(new Candidato("municipio", "Goiânia", "Goiânia")), false));
        var pool = Executors.newSingleThreadExecutor();
        try {
            var primeira = pool.submit(() -> participante.responderValor("Moro em Goiânia"));
            assertThat(entrou.await(5, TimeUnit.SECONDS)).isTrue();

            var outro = new ParticipanteApi(novoNavegador());
            outro.navegador.definirCookie("sessao_cadastro", participante.navegador.cookie("sessao_cadastro").getValue());
            outro.navegador.definirCookie("XSRF-TOKEN", participante.navegador.cookie("XSRF-TOKEN").getValue());
            outro.versao = participante.versao;
            outro.responderValor("Anápolis")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.mensagem").value(containsString("Aguarde a resposta anterior")))
                    .andExpect(header().exists("Retry-After"));

            liberar.countDown();
            primeira.get(5, TimeUnit.SECONDS).andExpect(status().isOk()).andExpect(jsonPath("$.proximaPergunta.campo").value("uf"));
        } finally {
            liberar.countDown();
            pool.shutdownNow();
        }

        assertThat(interpretador.pedidos).hasSize(1);
        // Terminada a operação, a sessão volta a aceitar respostas.
        participante.responderOpcao("GO").andExpect(status().isOk());
    }
}
