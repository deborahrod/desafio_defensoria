package io.github.deborahrod.defensoria.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ChaveDeOrigemTest {

    private static MockHttpServletRequest requisicao(String encaminhado) {
        var requisicao = new MockHttpServletRequest();
        requisicao.setRemoteAddr("192.0.2.10");
        if (encaminhado != null) {
            requisicao.addHeader("X-Forwarded-For", encaminhado);
        }
        return requisicao;
    }

    @Test
    void semProxyConfiavelOCabecalhoEncaminhadoEIgnoradoParaNaoPoderSerForjado() {
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("203.0.113.7"), false)).isEqualTo("192.0.2.10");
    }

    @Test
    void comProxyConfiavelVaiOPrimeiroEnderecoDaCadeia() {
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("203.0.113.7, 198.51.100.2"), true))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void semCabecalhoOuComCabecalhoVazioUsaOEnderecoDaConexao() {
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao(null), true)).isEqualTo("192.0.2.10");
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("  "), true)).isEqualTo("192.0.2.10");
    }

    @Test
    void valorGrandeDemaisNaoViraChaveIlimitada() {
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("x".repeat(500)), true)).hasSize(64);
    }
}
