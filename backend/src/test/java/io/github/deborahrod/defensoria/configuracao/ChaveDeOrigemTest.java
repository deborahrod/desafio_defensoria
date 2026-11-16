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
    void comProxyConfiavelVaiOUltimoEnderecoDaCadeia() {
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("203.0.113.7"), true)).isEqualTo("203.0.113.7");
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("198.51.100.2, 203.0.113.7"), true))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void entradasAnterioresDaCadeiaVieramDoClienteENaoMudamAChave() {
        var real = FiltroLimitePorOrigem.chaveDeOrigem(requisicao("1.1.1.1, 203.0.113.7"), true);

        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("2.2.2.2, 203.0.113.7"), true)).isEqualTo(real);
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao("9.9.9.9, 8.8.8.8, 203.0.113.7"), true)).isEqualTo(real);
    }

    @Test
    void cadeiaSemEnderecoUtilCaiNoEnderecoDaConexao() {
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao(" , "), true)).isEqualTo("192.0.2.10");
        assertThat(FiltroLimitePorOrigem.chaveDeOrigem(requisicao(",,"), true)).isEqualTo("192.0.2.10");
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
