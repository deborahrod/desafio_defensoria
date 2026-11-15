package io.github.deborahrod.defensoria.conversa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Registro mínimo para não reprocessar um envio repetido. Não guarda o texto da mensagem. */
@Entity
@Table(name = "mensagem_processada")
@IdClass(MensagemProcessada.Chave.class)
public class MensagemProcessada {

    public record Chave(UUID sessaoId, UUID mensagemId) implements Serializable {
    }

    @Id
    private UUID sessaoId;

    @Id
    private UUID mensagemId;

    private int versaoEntrada;
    private int versaoResultado;
    private Instant processadaEm;

    protected MensagemProcessada() {
    }

    public MensagemProcessada(UUID sessaoId, UUID mensagemId, int versaoEntrada, int versaoResultado, Instant processadaEm) {
        this.sessaoId = sessaoId;
        this.mensagemId = mensagemId;
        this.versaoEntrada = versaoEntrada;
        this.versaoResultado = versaoResultado;
        this.processadaEm = processadaEm;
    }

    public UUID getSessaoId() {
        return sessaoId;
    }

    public UUID getMensagemId() {
        return mensagemId;
    }

    public int getVersaoEntrada() {
        return versaoEntrada;
    }

    public int getVersaoResultado() {
        return versaoResultado;
    }

    public Instant getProcessadaEm() {
        return processadaEm;
    }
}
