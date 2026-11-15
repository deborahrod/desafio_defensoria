package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Vínculo informado por quem preenche em nome da titular. É declaração, não poder conferido. */
@Entity
@Table(name = "representacao_declarada")
public class RepresentacaoDeclarada {

    @Id
    private UUID pessoaId;

    private String nomeQuemPreenche;
    private String vinculoDeclarado;
    private Instant declaradaEm;

    protected RepresentacaoDeclarada() {
    }

    public RepresentacaoDeclarada(UUID pessoaId, String nomeQuemPreenche, String vinculoDeclarado, Instant declaradaEm) {
        this.pessoaId = pessoaId;
        this.nomeQuemPreenche = nomeQuemPreenche;
        this.vinculoDeclarado = vinculoDeclarado;
        this.declaradaEm = declaradaEm;
    }

    public UUID getPessoaId() {
        return pessoaId;
    }

    public String getNomeQuemPreenche() {
        return nomeQuemPreenche;
    }

    public String getVinculoDeclarado() {
        return vinculoDeclarado;
    }

    public Instant getDeclaradaEm() {
        return declaradaEm;
    }
}
