package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.Navegador;
import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.PessoaCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Sessão A contra sessão B: conhecer identificadores de A não dá acesso a nada de A. */
class IsolamentoEntreSessoesTest extends TesteIntegracao {

    private static final String COOKIE = "sessao_cadastro";

    @Autowired
    private SessaoCadastroRepository sessoes;

    @Autowired
    private RascunhoCadastroRepository rascunhos;

    @Autowired
    private PessoaCadastroRepository pessoas;

    private ParticipanteApi novo() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        return participante;
    }

    @Test
    void identificadorOuHashDaSessaoAlheiaNaoServemComoCredencial() throws Exception {
        var a = novo();
        a.responderTudo(RespostasFicticias.fluxoComum());
        var idDeA = sessoes.findAll().getFirst().getId();
        var hashDeA = sessoes.findAll().getFirst().getSegredoHash();

        for (var palpite : new String[] {idDeA.toString(), hashDeA}) {
            var intruso = novoNavegador();
            intruso.get("/atual");
            intruso.definirCookie(COOKIE, palpite);
            intruso.get("/atual").andExpect(status().isUnauthorized());
            intruso.get("/revisao").andExpect(status().isUnauthorized());
            intruso.post("/confirmacao", "{\"versaoRevisada\":%d,\"chaveIdempotencia\":\"%s\"}"
                            .formatted(a.versao, UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
            intruso.delete("/sessao").andExpect(status().isUnauthorized());
        }
        assertThat(sessoes.findById(idDeA)).get().extracting("estado").isEqualTo(EstadoSessao.EM_REVISAO);
        assertThat(rascunhos.findById(idDeA)).isPresent();
        assertThat(pessoas.count()).isZero();
    }

    @Test
    void asRotasNaoExpoemRecursosPorIdentificador() throws Exception {
        var a = novo();
        a.responderTudo(RespostasFicticias.fluxoComum());
        var b = novo();
        var idDeA = sessoes.findAll().stream().filter(s -> s.getEstado() == EstadoSessao.EM_REVISAO).findFirst().orElseThrow().getId();

        for (var caminho : new String[] {"/" + idDeA, "/sessao/" + idDeA, "/atual/" + idDeA, "/rascunho/" + idDeA, "/pessoas", "/lista"}) {
            b.navegador.get(caminho)
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string(not(containsString("Maria"))));
        }
        mvc.perform(get(Navegador.BASE + "?cadastroId=" + idDeA)).andExpect(status().is4xxClientError());
    }

    @Test
    void sessaoBNaoConfirmaNemCorrigeNemApagaORascunhoDeA() throws Exception {
        var a = novo();
        a.responderTudo(RespostasFicticias.fluxoComum());
        var b = novo();
        b.responderOpcao("PROPRIA_PESSOA");

        // B usa a versão e até a chave de confirmação que A usaria.
        b.confirmar(a.versao, UUID.randomUUID())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        b.corrigir("nome", "\"Nome Trocado Por B\"", "null").andExpect(status().isBadRequest());
        b.navegador.delete("/sessao").andExpect(status().isNoContent());

        assertThat(pessoas.count()).isZero();
        a.navegador.get("/atual")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_REVISAO"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='nome')].valor").value("Maria Fictícia da Silva"));
        a.confirmar().andExpect(status().isOk());
        assertThat(pessoas.findAll()).singleElement().extracting("nome").isEqualTo("Maria Fictícia da Silva");
    }

    @Test
    void cadaSessaoSoVeOsProprioDadosMesmoComRespostasSimultaneasNoMesmoPasso() throws Exception {
        var a = novo();
        var b = novo();
        a.responderOpcao("PROPRIA_PESSOA");
        b.responderOpcao("PROPRIA_PESSOA");
        a.responderValor("Pessoa Da Sessão A");
        b.responderValor("Pessoa Da Sessão B");

        a.navegador.get("/atual").andExpect(content().string(containsString("Pessoa Da Sessão A")))
                .andExpect(content().string(not(containsString("Pessoa Da Sessão B"))));
        b.navegador.get("/atual").andExpect(content().string(containsString("Pessoa Da Sessão B")))
                .andExpect(content().string(not(containsString("Pessoa Da Sessão A"))));
        assertThat(a.recebido).noneMatch(texto -> texto.contains("Pessoa Da Sessão B"));
        assertThat(b.recebido).noneMatch(texto -> texto.contains("Pessoa Da Sessão A"));
    }
}
