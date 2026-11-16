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

    /** Chama o provedor e, se o contrato falhar, mostra a regra violada (nunca o conteúdo). */
    private ResultadoInterpretacao interpretar(PedidoInterpretacao pedido) {
        try {
            return interpretador().interpretar(pedido);
        } catch (FalhaInterpretacaoException falha) {
            // Os textos dos testes são fictícios: pode mostrar o que o modelo devolveu.
            String bruto;
            try {
                bruto = interpretador().chamar(pedido);
            } catch (RuntimeException e) {
                bruto = "(sem conteúdo)";
            }
            throw new AssertionError("Falha " + falha.categoria() + ": " + falha.detalhe() + " | conteúdo: " + bruto, falha);
        }
    }

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

        var resultado = interpretar(pedido);

        assertThat(resultado.operacao()).isEqualTo(Operacao.PROPOR_VALORES);
        assertThat(resultado.candidatos())
                .anySatisfy(c -> {
                    assertThat(c.campo()).isEqualTo("municipio");
                    assertThat(ValidadorSaida.normalizar(c.valor())).contains("goiania");
                });
    }

    private static final List<String> UFS = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private static final List<PedidoInterpretacao.Topico> TOPICOS = List.of(
            new PedidoInterpretacao.Topico("dependentes", "o que são dependentes"),
            new PedidoInterpretacao.Topico("renda-mensal", "o que contar como renda mensal"),
            new PedidoInterpretacao.Topico("nucleo-familiar", "o que é núcleo familiar"));

    @Test
    void relatoLivreComVariosCamposEOpcoesFechadas() {
        var campos = List.of(
                new CampoPermitido("municipio", "município ou localidade onde a pessoa está"),
                new CampoPermitido("uf", "sigla do estado (UF) do município", UFS),
                new CampoPermitido("ocupacao", "ocupação ou situação de trabalho atual"),
                new CampoPermitido("renda", "renda mensal da pessoa, em reais, como número"),
                new CampoPermitido("quantidadeMembros", "quantas pessoas moram na mesma casa, contando a própria pessoa"),
                new CampoPermitido("dependentes", "quantas dessas pessoas dependem financeiramente da pessoa"),
                new CampoPermitido("situacaoMoradia", "situação da moradia atual",
                        List.of("PROPRIA", "ALUGADA", "CEDIDA", "ACOLHIMENTO", "OUTRA")));
        var pedido = new PedidoInterpretacao(
                "Conte onde você mora, o que faz e quem mora com você.",
                "Moro em Anápolis, alugado, com minha mãe e dois filhos. Faço bico de pedreiro, uns mil e quinhentos por mês.",
                campos,
                TOPICOS);

        var resultado = interpretar(pedido);

        assertThat(resultado.operacao()).isEqualTo(Operacao.PROPOR_VALORES);
        var valores = resultado.candidatos().stream().collect(java.util.stream.Collectors.toMap(c -> c.campo(), c -> c.valor()));
        assertThat(ValidadorSaida.normalizar(valores.get("municipio"))).isEqualTo("anapolis");
        assertThat(valores.get("situacaoMoradia")).isEqualTo("ALUGADA");
        assertThat(valores).containsKey("ocupacao");
        assertThat(valores.get("quantidadeMembros")).isEqualTo("4");
        assertThat(new java.math.BigDecimal(valores.get("renda"))).isEqualByComparingTo("1500");
        // A UF é opcional: o modelo só a sugere quando tem certeza. Se vier, tem de ser a certa.
        if (valores.containsKey("uf")) {
            assertThat(valores.get("uf")).isEqualTo("GO");
        }
    }

    @Test
    void municipioSemAcentoNemMaiusculaVoltaNormalizado() {
        var campos = List.of(
                new CampoPermitido("municipio", "município ou localidade onde a pessoa está"),
                new CampoPermitido("uf", "sigla do estado (UF) do município", UFS));
        var pedido = new PedidoInterpretacao("Em qual município ou localidade você está?", "aparecida de goiania", campos, TOPICOS);

        var resultado = interpretar(pedido);

        assertThat(resultado.operacao()).isEqualTo(Operacao.PROPOR_VALORES);
        assertThat(resultado.candidatos()).anySatisfy(c -> {
            assertThat(c.campo()).isEqualTo("municipio");
            assertThat(c.valor()).isEqualTo("Aparecida de Goiânia");
        });
    }

    @Test
    void duvidaSobreUmTermoViraTopicoDoCatalogo() {
        var pedido = new PedidoInterpretacao(
                "Contando com você, quantas pessoas vivem na mesma casa?", "o que é dependente?", List.of(), TOPICOS);

        var resultado = interpretar(pedido);

        assertThat(resultado.operacao()).isEqualTo(Operacao.AJUDA);
        assertThat(resultado.topico()).isEqualTo("dependentes");
    }
}
