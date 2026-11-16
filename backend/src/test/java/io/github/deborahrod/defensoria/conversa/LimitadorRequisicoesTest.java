package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.deborahrod.defensoria.integracao.ServicoEventos;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LimitadorRequisicoesTest {

    private static final class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-03-10T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioMutavel relogio = new RelogioMutavel();

    /** Teto de 2 requisições por minuto por origem e no máximo 3 origens acompanhadas. */
    private LimitadorRequisicoes limitador() {
        return new LimitadorRequisicoes(new PropriedadesLimites(60, 10, 2, true), relogio, mock(ServicoEventos.class), 3);
    }

    @Test
    void origemConhecidaTemOProprioTetoMesmoComOMapaCheio() {
        var limitador = limitador();
        limitador.permitirOrigem("a");
        limitador.permitirOrigem("b");
        limitador.permitirOrigem("c");

        assertThat(limitador.permitirOrigem("a")).isZero();
        assertThat(limitador.permitirOrigem("a")).isPositive();
        assertThat(limitador.permitirOrigem("b")).isZero();
    }

    @Test
    void oMapaNaoCrescePassandoDoMaximoQuandoChegamOrigensNovasEmSequencia() {
        var limitador = limitador();

        for (var i = 0; i < 1_000; i++) {
            limitador.permitirOrigem("forjada-" + i);
        }

        // No máximo as 3 origens mais o balde das excedentes.
        assertThat(limitador.origensAcompanhadas()).isLessThanOrEqualTo(4);
    }

    @Test
    void origensAlemDoMaximoDividemUmUnicoBalde() {
        var limitador = limitador();
        limitador.permitirOrigem("a");
        limitador.permitirOrigem("b");
        limitador.permitirOrigem("c");

        assertThat(limitador.permitirOrigem("nova-1")).isZero();
        assertThat(limitador.permitirOrigem("nova-2")).isZero();
        assertThat(limitador.permitirOrigem("nova-3")).isPositive();
        assertThat(limitador.permitirOrigem("nova-4")).isPositive();
    }

    @Test
    void depoisDeUmaJanelaSemPedirAsOrigensOciosasDaoLugarAsNovas() {
        var limitador = limitador();
        limitador.permitirOrigem("a");
        limitador.permitirOrigem("b");
        limitador.permitirOrigem("c");

        relogio.agora = relogio.agora.plus(Duration.ofSeconds(61));

        assertThat(limitador.permitirOrigem("nova-1")).isZero();
        assertThat(limitador.permitirOrigem("nova-1")).isZero();
        assertThat(limitador.permitirOrigem("nova-1")).isPositive();
        assertThat(limitador.origensAcompanhadas()).isLessThanOrEqualTo(3);
    }

    @Test
    void oMaximoPadraoEDeDezMilOrigens() {
        assertThat(LimitadorRequisicoes.MAXIMO_DE_ORIGENS).isEqualTo(10_000);
    }
}
