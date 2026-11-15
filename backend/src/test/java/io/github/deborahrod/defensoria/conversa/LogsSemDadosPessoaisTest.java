package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.opcao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "cadastro.llm.api-key=chave-secreta-de-teste")
class LogsSemDadosPessoaisTest extends TesteIntegracao {

    @Test
    void jornadaCompletaComFalhasNaoDeixaDadoPessoalNemChaveNosLogs(CapturedOutput saida) throws Exception {
        // Cadastro completo, escolhendo perguntas estruturadas na hora de decidir sobre a IA.
        var respostas = new ArrayList<>(RespostasFicticias.fluxoComum());
        respostas.add(9, opcao("ESTRUTURADO"));
        var primeira = new ParticipanteApi(novoNavegador());
        primeira.iniciar();
        primeira.responderTudo(respostas);
        primeira.responderValor("texto fora do formato esperado").andExpect(status().isConflict());
        primeira.confirmar().andExpect(status().isOk());

        // Segunda sessão: CPF repetido (conflito) e falha da IA com texto livre.
        var segunda = new ParticipanteApi(novoNavegador());
        segunda.iniciar();
        var outras = new ArrayList<>(RespostasFicticias.fluxoComum());
        outras.add(9, opcao("USAR_IA"));
        segunda.responderTudo(outras.subList(0, 10));
        interpretador.falharCom(Categoria.NAO_AUTORIZADO);
        segunda.responderValor("Moro em Goiânia e meu nome é Maria Fictícia da Silva").andExpect(status().isOk());
        segunda.responderTudo(outras.subList(10, outras.size()).stream().skip(0).toList());
        segunda.confirmar().andExpect(status().isConflict());

        var logs = saida.getAll();
        assertThat(logs).contains("evento tipo=INFERENCIA status=NAO_AUTORIZADO");
        assertThat(logs).contains("evento tipo=CONFIRMACAO status=SUCESSO");
        assertThat(logs).contains("evento tipo=CONFIRMACAO status=CONFLITO_CADASTRAL");
        assertThat(logs)
                .doesNotContain("Maria")
                .doesNotContain("Fictícia")
                .doesNotContain("529.982.247-25")
                .doesNotContain("52998224725")
                .doesNotContain("91234")
                .doesNotContain("Goiânia")
                .doesNotContain("Diarista")
                .doesNotContain("1.518")
                .doesNotContain("chave-secreta-de-teste")
                .doesNotContainIgnoringCase("authorization")
                .doesNotContainIgnoringCase("bearer");
    }
}
