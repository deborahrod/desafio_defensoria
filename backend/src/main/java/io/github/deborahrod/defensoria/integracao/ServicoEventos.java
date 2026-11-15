package io.github.deborahrod.defensoria.integracao;

import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra eventos técnicos sem dado pessoal, sem chave e sem conteúdo da conversa. */
@Service
public class ServicoEventos {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoEventos.class);

    private final EventoTecnicoRepository eventos;
    private final Clock relogio;

    public ServicoEventos(EventoTecnicoRepository eventos, Clock relogio) {
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String tipo, String status, ModoInteracao modo, String modelo, Long duracaoMs) {
        try {
            eventos.save(new EventoTecnico(tipo, status, modo, modelo, duracaoMs, UUID.randomUUID(), relogio.instant()));
        } catch (RuntimeException e) {
            // Falha ao registrar métrica não pode derrubar o cadastro.
            LOG.warn("Evento técnico não registrado: {}", e.getClass().getSimpleName());
        }
        LOG.info("evento tipo={} status={} modo={} duracaoMs={}", tipo, status, modo, duracaoMs);
    }
}
