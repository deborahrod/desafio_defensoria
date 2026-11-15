package io.github.deborahrod.defensoria.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.CampoPermitido;
import io.github.deborahrod.defensoria.integracao.ResultadoInterpretacao.Operacao;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Chamada real ao provedor configurado. Só roda com LLM_API_KEY no ambiente e usa apenas texto fictício.
 * Para executar: carregue o .env no shell e rode ./mvnw test -Dtest=InterpretadorRealTest.
 */
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class InterpretadorRealTest {

    private static final List<CampoPermitido> CAMPOS = List.of(
            new CampoPermitido("municipio", "município ou localidade onde a pessoa está"),
            new CampoPermitido("ocupacao", "ocupação ou situação de trabalho atual"));

    private InterpretadorChatCompletions interpretador() {
        var base = System.getenv("LLM_BASE_URL");
        var modelo = System.getenv("LLM_MODEL");
        var llm = new PropriedadesLlm(System.getenv("LLM_API_KEY"), base, modelo, Duration.ofSeconds(30), 350);
        return new InterpretadorChatCompletions(llm);
    }

    @Test
    void interpretaUmaRespostaComDoisDadosEPassaNaValidacaoDoContrato() {
        var pedido = new PedidoInterpretacao(
                "Em qual município ou localidade você está?", "Moro em Goiânia e estou desempregada", CAMPOS);

        var resultado = interpretador().interpretar(pedido);

        assertThat(resultado.operacao()).isEqualTo(Operacao.PROPOR_VALORES);
        assertThat(resultado.candidatos())
                .anySatisfy(c -> {
                    assertThat(c.campo()).isEqualTo("municipio");
                    assertThat(ValidadorSaida.normalizar(c.valor())).contains("goiania");
                });
    }
}
