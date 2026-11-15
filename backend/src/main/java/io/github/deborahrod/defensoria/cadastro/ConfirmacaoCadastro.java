package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "confirmacao_cadastro")
public class ConfirmacaoCadastro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID sessaoId;
    private int versaoRevisada;
    private UUID chaveIdempotencia;
    private UUID cadastroId;
    private boolean comPendencias;
    private Instant confirmadoEm;

    protected ConfirmacaoCadastro() {
    }

    public ConfirmacaoCadastro(
            UUID sessaoId,
            int versaoRevisada,
            UUID chaveIdempotencia,
            UUID cadastroId,
            boolean comPendencias,
            Instant confirmadoEm) {
        this.sessaoId = sessaoId;
        this.versaoRevisada = versaoRevisada;
        this.chaveIdempotencia = chaveIdempotencia;
        this.cadastroId = cadastroId;
        this.comPendencias = comPendencias;
        this.confirmadoEm = confirmadoEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessaoId() {
        return sessaoId;
    }

    public int getVersaoRevisada() {
        return versaoRevisada;
    }

    public UUID getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public UUID getCadastroId() {
        return cadastroId;
    }

    public boolean isComPendencias() {
        return comPendencias;
    }

    public Instant getConfirmadoEm() {
        return confirmadoEm;
    }
}
