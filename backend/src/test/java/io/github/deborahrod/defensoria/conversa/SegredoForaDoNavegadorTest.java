package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.opcao;
import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** A chave do provedor só existe no servidor: nenhuma resposta da API a carrega, nem em erro. */
@TestPropertySource(properties = {
    "cadastro.llm.api-key=sk-chave-que-nao-pode-chegar-ao-navegador",
    "cadastro.llm.base-url=https://provedor-secreto.example/v1"
})
class SegredoForaDoNavegadorTest extends TesteIntegracao {

    private static final String CHAVE = "sk-chave-que-nao-pode-chegar-ao-navegador";

    private final List<String> recebido = new ArrayList<>();

    private ResultActions registrar(ResultActions resposta) throws Exception {
        var http = resposta.andReturn().getResponse();
        recebido.add(http.getContentAsString());
        http.getHeaderNames().forEach(nome -> recebido.add(nome + ": " + http.getHeaders(nome)));
        return resposta;
    }

    @Test
    void respostasDeSucessoErroELimiteNaoContemAChaveNemOEnderecoDoProvedor() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        registrar(participante.iniciar());
        participante.responderTudo(List.of(
                opcao("PROPRIA_PESSOA"), valor("Ana Fictícia"), opcao("PULAR"), opcao("NAO_SEI"),
                opcao("NAO_POSSUI"), opcao("INDISPONIVEL"), opcao("USAR_IA")));

        interpretador.propor("municipio", "Goiânia", "Goiânia", "ocupacao", "desempregada", "desempregada");
        registrar(participante.responderValor("Moro em Goiânia e estou desempregada"));
        registrar(participante.responderOpcao("CONFIRMAR"));
        interpretador.falharCom(Categoria.NAO_AUTORIZADO);
        registrar(participante.navegador.get("/atual"));
        registrar(participante.responderOpcao("GO"));
        registrar(participante.responderOpcao("OPCAO_INEXISTENTE").andExpect(status().isBadRequest()));
        registrar(participante.responder(java.util.UUID.randomUUID(), 0, "\"x\"", "null").andExpect(status().isConflict()));
        registrar(participante.navegador.get("/rota-inexistente"));
        registrar(participante.navegador.post("/respostas", "{malformado"));
        registrar(participante.navegador.get("/revisao").andExpect(status().isConflict()));

        assertThat(recebido).isNotEmpty().noneMatch(texto -> texto.contains(CHAVE) || texto.contains("provedor-secreto"));
        assertThat(participante.recebido).noneMatch(texto -> texto.contains(CHAVE) || texto.contains("provedor-secreto"));
    }

    @Test
    void endpointsDeDiagnosticoNaoExpoemConfiguracao() throws Exception {
        for (var caminho : new String[] {"/actuator/env", "/actuator/configprops", "/actuator/beans", "/actuator/mappings"}) {
            mvc.perform(get(caminho)).andExpect(status().isNotFound());
        }
        var saude = mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(saude).isEqualTo("{\"status\":\"UP\"}");
    }
}
