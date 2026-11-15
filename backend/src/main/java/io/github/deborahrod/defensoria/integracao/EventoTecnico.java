package io.github.deborahrod.defensoria.integracao;

import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_tecnico")
public class EventoTecnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo;
    private String status;

    @Enumerated(EnumType.STRING)
    private ModoInteracao modo;

    private String modelo;
    private Long duracaoMs;
    private UUID correlacao;
    private Instant ocorridoEm;

    protected EventoTecnico() {
    }

    public EventoTecnico(
            String tipo,
            String status,
            ModoInteracao modo,
            String modelo,
            Long duracaoMs,
            UUID correlacao,
            Instant ocorridoEm) {
        this.tipo = tipo;
        this.status = status;
        this.modo = modo;
        this.modelo = modelo;
        this.duracaoMs = duracaoMs;
        this.correlacao = correlacao;
        this.ocorridoEm = ocorridoEm;
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getStatus() {
        return status;
    }

    public ModoInteracao getModo() {
        return modo;
    }

    public String getModelo() {
        return modelo;
    }

    public Long getDuracaoMs() {
        return duracaoMs;
    }

    public UUID getCorrelacao() {
        return correlacao;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }
}
