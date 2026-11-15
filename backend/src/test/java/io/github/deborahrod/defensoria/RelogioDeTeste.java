package io.github.deborahrod.defensoria;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Relógio que só anda quando o teste manda. */
public class RelogioDeTeste extends Clock {

    private static final Instant INICIO = Instant.parse("2026-11-15T12:00:00Z");

    private Instant agora = INICIO;

    public void avancar(Duration duracao) {
        agora = agora.plus(duracao);
    }

    public void reiniciar() {
        agora = INICIO;
    }

    @Override
    public Instant instant() {
        return agora;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }
}
