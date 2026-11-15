package io.github.deborahrod.defensoria.cadastro;

import static io.github.deborahrod.defensoria.cadastro.EstadoSessao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EstadoSessaoTest {

    @Test
    void confirmacaoSoEAlcancadaPelaRevisao() {
        assertThat(EM_REVISAO.podeIrPara(CONFIRMADO)).isTrue();
        assertThat(EM_PREENCHIMENTO.podeIrPara(CONFIRMADO)).isFalse();
        assertThat(AGUARDANDO_ESCLARECIMENTO.podeIrPara(CONFIRMADO)).isFalse();
    }

    @Test
    void preenchimentoEsclarecimentoERevisaoSeAlternam() {
        assertThat(EM_PREENCHIMENTO.podeIrPara(AGUARDANDO_ESCLARECIMENTO)).isTrue();
        assertThat(AGUARDANDO_ESCLARECIMENTO.podeIrPara(EM_PREENCHIMENTO)).isTrue();
        assertThat(EM_PREENCHIMENTO.podeIrPara(EM_REVISAO)).isTrue();
        assertThat(EM_REVISAO.podeIrPara(EM_PREENCHIMENTO)).isTrue();
        assertThat(EM_REVISAO.podeIrPara(AGUARDANDO_ESCLARECIMENTO)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(names = {"CONFIRMADO", "CANCELADO", "EXPIRADO"})
    void estadosFinaisNaoTemSaida(EstadoSessao estadoFinal) {
        for (var destino : values()) {
            assertThat(estadoFinal.podeIrPara(destino)).isFalse();
        }
        assertThat(estadoFinal.emAndamento()).isFalse();
    }

    @Test
    void sessaoRecusaTransicaoForaDaMaquinaDeEstados() {
        var sessao = new SessaoCadastro("hash", ModoInteracao.ESTRUTURADO, Instant.EPOCH, Duration.ofMinutes(30));

        assertThatThrownBy(() -> sessao.mudarPara(CONFIRMADO)).isInstanceOf(IllegalStateException.class);

        sessao.mudarPara(EM_REVISAO);
        sessao.mudarPara(CONFIRMADO);
        assertThatThrownBy(sessao::cancelar).isInstanceOf(IllegalStateException.class);
        assertThat(sessao.getEstado()).isEqualTo(CONFIRMADO);
    }
}
