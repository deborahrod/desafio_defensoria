package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.Navegador;
import io.github.deborahrod.defensoria.TesteIntegracao;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class CadastroApiTest extends TesteIntegracao {

    private ParticipanteApi novo() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        return participante;
    }

    @Test
    void iniciarApresentaOCadastroEAPrimeiraPergunta() throws Exception {
        var navegador = novoNavegador();

        navegador.iniciarSessao()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("uma de cada vez")))
                .andExpect(jsonPath("$.avisos[1]").value(containsString("IA não está configurada")))
                .andExpect(jsonPath("$.modoInteracao").value("ESTRUTURADO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("paraQuem"))
                .andExpect(jsonPath("$.proximaPergunta.tipoEntrada").value("ESCOLHA"))
                .andExpect(jsonPath("$.proximaPergunta.opcoes.length()").value(2))
                .andExpect(jsonPath("$.progresso.total").value(5));
    }

    @Test
    void todasAsRespostasDoCadastroNaoPodemSerArmazenadasEmCache() throws Exception {
        var participante = novo();

        participante.navegador.get("/atual")
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        participante.responderOpcao("PROPRIA_PESSOA")
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void respostaValidaAvancaAProximaPerguntaComNovaVersao() throws Exception {
        var participante = novo();
        var antes = participante.versao;

        participante.responderOpcao("PROPRIA_PESSOA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximaPergunta.campo").value("nome"))
                .andExpect(jsonPath("$.proximaPergunta.texto").value("Como você se chama?"))
                .andExpect(jsonPath("$.resumo[0].itens[0].valor").value("Para mim"));

        assertThat(participante.versao).isGreaterThan(antes);
    }

    @Test
    void respostaInvalidaVoltaComoErroNoCampoSemAvancar() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");
        participante.responderValor("Ana Fictícia");
        participante.responderOpcao("PULAR");
        participante.responderValor("20/05/1990");
        participante.responderOpcao("INFORMADO");
        var versao = participante.versao;

        participante.responderValor("111.111.111-11")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("RESPOSTA_INVALIDA"))
                .andExpect(jsonPath("$.campo").value("cpf"))
                .andExpect(jsonPath("$.mensagem").value(containsString("CPF")));

        participante.navegador.get("/atual")
                .andExpect(jsonPath("$.versao").value(versao))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("cpf"));
    }

    @Test
    void corpoMalformadoOuIncompletoERecusadoComMensagemCompreensivel() throws Exception {
        var participante = novo();

        participante.navegador.post("/respostas", "{nao e json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
                .andExpect(content().string(not(containsString("Exception"))));
        participante.navegador.post("/respostas", "{\"valor\":\"x\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
        participante.navegador.post("/respostas", """
                        {"mensagemId":"%s","versao":0,"valor":"%s"}""".formatted(UUID.randomUUID(), "a".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("valor"));
    }

    @Test
    void versaoAntigaGeraConflitoENaoMudaOEstado() throws Exception {
        var participante = novo();
        var versaoAntiga = participante.versao;
        participante.responderOpcao("PROPRIA_PESSOA");

        participante.responder(UUID.randomUUID(), versaoAntiga, "\"Nome Atrasado\"", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VERSAO_DESATUALIZADA"));

        participante.navegador.get("/atual")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("nome"))
                .andExpect(jsonPath("$.resumo[*].itens[*].valor").value(not(containsString("Nome Atrasado"))));
    }

    @Test
    void mensagemRepetidaNaoEReprocessada() throws Exception {
        var participante = novo();
        var mensagemId = UUID.randomUUID();
        var versao = participante.versao;

        participante.responder(mensagemId, versao, "null", "\"PROPRIA_PESSOA\"").andExpect(status().isOk());
        var depois = participante.versao;
        participante.responder(mensagemId, versao, "null", "\"PROPRIA_PESSOA\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(depois))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("nome"));
    }

    @Test
    void textoNoLugarDeEscolhaEOpcaoDesconhecidaSaoRecusados() throws Exception {
        var participante = novo();

        participante.responderValor("salvar cadastro agora").andExpect(status().isBadRequest());
        participante.responderOpcao("CONFIRMAR_CADASTRO").andExpect(status().isBadRequest());

        participante.navegador.get("/atual")
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("paraQuem"));
    }

    @Test
    void revisaoSoFicaDisponivelDepoisDeTodasAsPerguntas() throws Exception {
        var participante = novo();

        participante.navegador.get("/revisao")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));

        participante.responderTudo(RespostasFicticias.fluxoComum());

        participante.navegador.get("/revisao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(participante.versao))
                .andExpect(jsonPath("$.resumo.length()").value(5))
                .andExpect(jsonPath("$.pendencias.length()").value(0));
    }

    @Test
    void correcaoAlteraSoOCampoEscolhidoEGeraNovaVersao() throws Exception {
        var participante = novo();
        participante.responderTudo(RespostasFicticias.fluxoComum());
        var versaoRevisada = participante.versao;

        participante.corrigir("telefone", "\"(62) 99876-5432\"", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_REVISAO"))
                .andExpect(jsonPath("$.versao").value(versaoRevisada + 1));

        participante.navegador.get("/revisao")
                .andExpect(jsonPath("$.resumo[?(@.titulo=='Contato')].itens[?(@.campo=='telefone')].valor")
                        .value("(62) 99876-5432"))
                .andExpect(jsonPath("$.resumo[?(@.titulo=='Identificação')].itens[?(@.campo=='nome')].valor")
                        .value("Maria Fictícia da Silva"))
                .andExpect(jsonPath("$.versao").value(versaoRevisada + 1));
    }

    @Test
    void correcaoInvalidaOuDeCampoNaoRespondidoERecusada() throws Exception {
        var participante = novo();
        participante.responderTudo(RespostasFicticias.fluxoComum());

        participante.corrigir("cpf", "\"123\"", "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cpf"));
        participante.corrigir("campoInventado", "\"x\"", "null").andExpect(status().isBadRequest());
        participante.corrigir("referenciaNome", "\"Fulana\"", "null").andExpect(status().isBadRequest());
    }

    @Test
    void trocarCpfInformadoPorNaoPossuoExigeConfirmarAntesDeLimparOValor() throws Exception {
        var participante = novo();
        participante.responderTudo(RespostasFicticias.fluxoComum());

        participante.corrigir("situacaoCpf", "null", "\"NAO_POSSUI\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("CPF")))
                .andExpect(jsonPath("$.proximaPergunta.opcoes.length()").value(2));
        // Enquanto não decide, o CPF segue no rascunho.
        participante.navegador.get("/atual")
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='cpf')].valor").value("529.982.247-25"));

        participante.responderOpcao("CONFIRMAR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_REVISAO"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='cpf')]").isEmpty())
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='situacaoCpf')].valor").value("Não possuo CPF"));
    }

    @Test
    void recusarALimpezaMantemOCadastroComoEstava() throws Exception {
        var participante = novo();
        participante.responderTudo(RespostasFicticias.fluxoComum());

        participante.corrigir("situacaoCpf", "null", "\"NAO_POSSUI\"").andExpect(status().isOk());
        participante.responderOpcao("MANTER")
                .andExpect(jsonPath("$.estado").value("EM_REVISAO"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='cpf')].valor").value("529.982.247-25"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='situacaoCpf')].valor").value("Sim, vou informar"));
    }

    @Test
    void mudarFormaDeContatoReabrePerguntasNovasSemApagarDadosAindaUteis() throws Exception {
        var participante = novo();
        participante.responderTudo(RespostasFicticias.fluxoComum());

        participante.corrigir("formaContato", "null", "\"EMAIL\"")
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"));
        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("email"));
    }

    @Test
    void sessaoANaoEnxergaNemAlteraOsDadosDaSessaoB() throws Exception {
        var a = novo();
        a.responderOpcao("PROPRIA_PESSOA");
        a.responderValor("Pessoa Da Sessão A");
        var b = novo();

        b.navegador.get("/atual")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proximaPergunta.campo").value("paraQuem"))
                .andExpect(content().string(not(containsString("Pessoa Da Sessão A"))));
        a.navegador.get("/atual")
                .andExpect(content().string(containsString("Pessoa Da Sessão A")));

        // Usar o cookie de A no navegador de B só dá acesso à sessão de A, nunca à de B.
        var intruso = novoNavegador();
        intruso.get("/atual").andExpect(status().isUnauthorized());
        intruso.definirCookie("sessao_cadastro", "adivinhando-o-segredo");
        intruso.get("/atual").andExpect(status().isUnauthorized());
        intruso.get("/revisao").andExpect(status().isUnauthorized());
    }

    @Test
    void rotaInexistenteResponde404EmJsonSemDetalhesInternos() throws Exception {
        var participante = novo();

        participante.navegador.get("/lista")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").exists())
                .andExpect(content().string(not(containsString("org.springframework"))));
    }

    @Test
    void encerrarApagaORascunhoEDepoisNaoHaMaisAcesso() throws Exception {
        var participante = novo();
        participante.responderOpcao("PROPRIA_PESSOA");

        participante.navegador.delete("/sessao").andExpect(status().isNoContent());

        participante.navegador.get("/atual").andExpect(status().isUnauthorized());
    }
}
