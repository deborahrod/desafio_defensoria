package io.github.deborahrod.defensoria.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.Navegador;
import io.github.deborahrod.defensoria.TesteIntegracao;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessaoPrivadaTest extends TesteIntegracao {

    private static final String COOKIE = CookieSessao.NOME;

    @Autowired
    private SessaoCadastroRepository sessoes;

    @Autowired
    private RascunhoCadastroRepository rascunhos;

    @Autowired
    private ServicoSessao servico;

    @Test
    void iniciarDefineCookieProtegidoEGuardaApenasOHash() throws Exception {
        var navegador = novoNavegador();

        var resposta = navegador.iniciarSessao()
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().sameSite(COOKIE, "Strict"))
                .andExpect(cookie().path(COOKIE, "/api"))
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.minutosDeInatividade").value(30))
                .andReturn().getResponse();

        var segredo = navegador.cookie(COOKIE).getValue();
        assertThat(resposta.getContentAsString()).doesNotContain(segredo);
        var sessao = sessaoDe(navegador);
        assertThat(sessao.getSegredoHash()).isNotEqualTo(segredo).hasSize(64);
        assertThat(rascunhos.findById(sessao.getId())).isPresent();
    }

    @Test
    void semCookieOuComCookieDesconhecidoNaoHaAcesso() throws Exception {
        var navegador = novoNavegador();

        navegador.get("/atual")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INEXISTENTE"));

        navegador.definirCookie(COOKIE, "segredo-que-nao-existe");
        navegador.get("/atual").andExpect(status().isUnauthorized());
    }

    @Test
    void mutacaoSemTokenCsrfERecusada() throws Exception {
        mvc.perform(post(Navegador.BASE + "/sessao"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("REQUISICAO_RECUSADA"));

        var navegador = novoNavegador();
        navegador.iniciarSessao();
        navegador.esquecerCookie("XSRF-TOKEN");
        navegador.delete("/sessao").andExpect(status().isForbidden());
    }

    @Test
    void respostasDoCadastroNaoPodemSerArmazenadasEmCache() throws Exception {
        var navegador = novoNavegador();
        navegador.iniciarSessao();

        navegador.get("/atual")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    void cadaAcessoRenovaOPrazoDeInatividade() throws Exception {
        var navegador = novoNavegador();
        navegador.iniciarSessao();

        relogio.avancar(Duration.ofMinutes(20));
        navegador.get("/atual").andExpect(status().isOk());
        relogio.avancar(Duration.ofMinutes(20));

        navegador.get("/atual").andExpect(status().isOk());
    }

    @Test
    void sessaoExpiradaNaoDevolveDadosEApagaORascunho() throws Exception {
        var navegador = novoNavegador();
        navegador.iniciarSessao();
        var id = sessaoDe(navegador).getId();
        var segredo = navegador.cookie(COOKIE).getValue();

        relogio.avancar(Duration.ofMinutes(31));

        navegador.get("/atual")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("SESSAO_EXPIRADA"))
                .andExpect(cookie().maxAge(COOKIE, 0))
                .andExpect(content().string(Matchers.not(Matchers.containsString("EM_PREENCHIMENTO"))));
        assertThat(sessoes.findById(id)).get().extracting(SessaoCadastro::getEstado).isEqualTo(EstadoSessao.EXPIRADO);
        assertThat(rascunhos.findById(id)).isEmpty();

        navegador.definirCookie(COOKIE, segredo);
        navegador.get("/atual").andExpect(status().isGone());
    }

    @Test
    void rotinaDeLimpezaApagaRascunhosExpiradosEDepoisAsSessoes() throws Exception {
        var navegador = novoNavegador();
        navegador.iniciarSessao();
        var id = sessaoDe(navegador).getId();

        relogio.avancar(Duration.ofMinutes(31));
        servico.limparExpiradas();

        assertThat(rascunhos.findById(id)).isEmpty();
        assertThat(sessoes.findById(id)).get().extracting(SessaoCadastro::getEstado).isEqualTo(EstadoSessao.EXPIRADO);

        relogio.avancar(Duration.ofHours(25));
        servico.limparExpiradas();

        assertThat(sessoes.findById(id)).isEmpty();
    }

    @Test
    void encerrarRemoveORascunhoEInvalidaOCookie() throws Exception {
        var navegador = novoNavegador();
        navegador.iniciarSessao();
        var id = sessaoDe(navegador).getId();
        var segredo = navegador.cookie(COOKIE).getValue();

        navegador.delete("/sessao")
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));

        assertThat(rascunhos.findById(id)).isEmpty();
        assertThat(sessoes.findById(id)).get().extracting(SessaoCadastro::getEstado).isEqualTo(EstadoSessao.CANCELADO);
        navegador.definirCookie(COOKIE, segredo);
        navegador.get("/atual").andExpect(status().isUnauthorized());
    }

    @Test
    void iniciarDeNovoEncerraASessaoAnterior() throws Exception {
        var navegador = novoNavegador();
        navegador.iniciarSessao();
        var anterior = sessaoDe(navegador).getId();

        navegador.post("/sessao").andExpect(status().isCreated());

        assertThat(sessaoDe(navegador).getId()).isNotEqualTo(anterior);
        assertThat(rascunhos.findById(anterior)).isEmpty();
    }

    @Test
    void corsAceitaApenasOrigensConfiguradas() throws Exception {
        mvc.perform(options(Navegador.BASE + "/sessao")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mvc.perform(options(Navegador.BASE + "/sessao")
                        .header("Origin", "https://site-externo.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    private SessaoCadastro sessaoDe(Navegador navegador) {
        var segredo = navegador.cookie(COOKIE).getValue();
        return sessoes.findBySegredoHash(SegredoSessao.hash(segredo)).orElseThrow();
    }
}
