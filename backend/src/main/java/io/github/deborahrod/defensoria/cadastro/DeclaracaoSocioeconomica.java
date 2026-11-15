package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Retrato datado do que a pessoa declarou. Não é avaliação de elegibilidade. */
@Entity
@Table(name = "declaracao_socioeconomica")
public class DeclaracaoSocioeconomica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID pessoaId;
    private UUID sessaoOrigemId;
    private LocalDate competencia;
    private String ocupacao;
    private BigDecimal rendaDeclarada;
    private Integer quantidadeMembros;
    private Integer dependentes;

    @Enumerated(EnumType.STRING)
    private SituacaoMoradia situacaoMoradia;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> ausencias;

    private Instant coletadaEm;

    protected DeclaracaoSocioeconomica() {
    }

    public DeclaracaoSocioeconomica(
            UUID pessoaId,
            UUID sessaoOrigemId,
            LocalDate competencia,
            String ocupacao,
            BigDecimal rendaDeclarada,
            Integer quantidadeMembros,
            Integer dependentes,
            SituacaoMoradia situacaoMoradia,
            Map<String, String> ausencias,
            Instant coletadaEm) {
        this.pessoaId = pessoaId;
        this.sessaoOrigemId = sessaoOrigemId;
        this.competencia = competencia;
        this.ocupacao = ocupacao;
        this.rendaDeclarada = rendaDeclarada;
        this.quantidadeMembros = quantidadeMembros;
        this.dependentes = dependentes;
        this.situacaoMoradia = situacaoMoradia;
        this.ausencias = Map.copyOf(ausencias);
        this.coletadaEm = coletadaEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPessoaId() {
        return pessoaId;
    }

    public UUID getSessaoOrigemId() {
        return sessaoOrigemId;
    }

    public LocalDate getCompetencia() {
        return competencia;
    }

    public String getOcupacao() {
        return ocupacao;
    }

    public BigDecimal getRendaDeclarada() {
        return rendaDeclarada;
    }

    public Integer getQuantidadeMembros() {
        return quantidadeMembros;
    }

    public Integer getDependentes() {
        return dependentes;
    }

    public SituacaoMoradia getSituacaoMoradia() {
        return situacaoMoradia;
    }

    public Map<String, String> getAusencias() {
        return ausencias;
    }

    public Instant getColetadaEm() {
        return coletadaEm;
    }
}
