package io.github.deborahrod.defensoria.conversa;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.deborahrod.defensoria.Navegador;
import java.util.List;
import java.util.UUID;
import org.springframework.test.web.servlet.ResultActions;

/** Um participante da API: guarda a versão devolvida para enviar no turno seguinte. */
public final class ParticipanteApi {

    public final Navegador navegador;
    public int versao;

    public ParticipanteApi(Navegador navegador) {
        this.navegador = navegador;
    }

    public ResultActions iniciar() throws Exception {
        return guardar(navegador.iniciarSessao());
    }

    public ResultActions responderOpcao(String opcao) throws Exception {
        return responder(UUID.randomUUID(), versao, "null", "\"" + opcao + "\"");
    }

    public ResultActions responderValor(String valor) throws Exception {
        return responder(UUID.randomUUID(), versao, "\"" + valor + "\"", "null");
    }

    public ResultActions responder(UUID mensagemId, int versaoEnviada, String valor, String opcao) throws Exception {
        var json = """
                {"mensagemId":"%s","versao":%d,"valor":%s,"opcao":%s}""".formatted(mensagemId, versaoEnviada, valor, opcao);
        return guardar(navegador.post("/respostas", json));
    }

    public ResultActions corrigir(String campo, String valor, String opcao) throws Exception {
        var json = """
                {"versao":%d,"valor":%s,"opcao":%s}""".formatted(versao, valor, opcao);
        return guardar(navegador.patch("/campos/" + campo, json));
    }

    public ResultActions confirmar() throws Exception {
        return confirmar(versao, UUID.randomUUID());
    }

    public ResultActions confirmar(int versaoRevisada, UUID chave) throws Exception {
        var json = """
                {"versaoRevisada":%d,"chaveIdempotencia":"%s"}""".formatted(versaoRevisada, chave);
        return guardar(navegador.post("/confirmacao", json));
    }

    public void responderTudo(List<RespostaEntrada> respostas) throws Exception {
        for (var resposta : respostas) {
            var resultado = resposta.opcao() != null ? responderOpcao(resposta.opcao()) : responderValor(resposta.valor());
            resultado.andExpect(status().isOk());
        }
    }

    private ResultActions guardar(ResultActions resposta) throws Exception {
        var http = resposta.andReturn().getResponse();
        var corpo = http.getContentAsString();
        if (http.getStatus() < 300 && corpo.contains("\"versao\"")) {
            versao = JsonPath.read(corpo, "$.versao");
        }
        return resposta;
    }
}
