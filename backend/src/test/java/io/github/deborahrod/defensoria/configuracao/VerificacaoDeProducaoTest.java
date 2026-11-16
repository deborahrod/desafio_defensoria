package io.github.deborahrod.defensoria.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.deborahrod.defensoria.cadastro.PropriedadesSessao;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class VerificacaoDeProducaoTest {

    private static final String SENHA_PROPRIA = "uma-senha-propria-de-teste";

    private ApplicationContextRunner contexto(boolean cookieSeguro, boolean perfilProd) {
        var sessao = new PropriedadesSessao(Duration.ofMinutes(30), cookieSeguro, Duration.ofHours(24));
        return new ApplicationContextRunner()
                .withBean(PropriedadesSessao.class, () -> sessao)
                .withUserConfiguration(VerificacaoDeProducao.class)
                .withInitializer(ctx -> {
                    if (perfilProd) {
                        ctx.getEnvironment().setActiveProfiles("prod");
                    }
                });
    }

    @Test
    void cookieSemSecureEMensagemNomeiaAVariavel() {
        assertThat(VerificacaoDeProducao.problemas(false, SENHA_PROPRIA))
                .singleElement().asString().contains("SESSAO_COOKIE_SEGURO");
    }

    @Test
    void senhaPadraoOuVaziaEMensagemNomeiaAVariavel() {
        for (var senha : new String[] {VerificacaoDeProducao.SENHA_PADRAO, "", "   ", null}) {
            assertThat(VerificacaoDeProducao.problemas(true, senha)).singleElement().asString().contains("DB_PASSWORD");
        }
    }

    @Test
    void configuracaoSeguraNaoTemProblemas() {
        assertThat(VerificacaoDeProducao.problemas(true, SENHA_PROPRIA)).isEmpty();
    }

    @Test
    void naoApareceNenhumValorNaMensagem() {
        assertThat(String.join(" ", VerificacaoDeProducao.problemas(false, VerificacaoDeProducao.SENHA_PADRAO)))
                .doesNotContain(VerificacaoDeProducao.SENHA_PADRAO);
    }

    @Test
    void comPerfilProdOContextoNaoSobeComCookieInseguro() {
        contexto(false, true).withPropertyValues("spring.datasource.password=" + SENHA_PROPRIA).run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(contexto.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(contexto.getStartupFailure().getMessage() + rootMessage(contexto.getStartupFailure()))
                    .contains("SESSAO_COOKIE_SEGURO");
        });
    }

    @Test
    void comPerfilProdOContextoNaoSobeComASenhaPadrao() {
        contexto(true, true).withPropertyValues("spring.datasource.password=cadastro_local").run(contexto -> {
            assertThat(contexto).hasFailed();
            assertThat(rootMessage(contexto.getStartupFailure())).contains("DB_PASSWORD");
        });
    }

    @Test
    void comPerfilProdEConfiguracaoSeguraOContextoSobe() {
        contexto(true, true).withPropertyValues("spring.datasource.password=" + SENHA_PROPRIA)
                .run(contexto -> assertThat(contexto).hasNotFailed().hasSingleBean(VerificacaoDeProducao.class));
    }

    @Test
    void semPerfilProdOsPadroesDeDemonstracaoContinuamValendo() {
        contexto(false, false).run(contexto -> assertThat(contexto).hasNotFailed().doesNotHaveBean(VerificacaoDeProducao.class));
    }

    private static String rootMessage(Throwable erro) {
        var causa = erro;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa.getMessage();
    }
}
