package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pessoa_cadastro")
public class PessoaCadastro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nome;
    private String nomeSocial;
    private LocalDate nascimento;

    @Enumerated(EnumType.STRING)
    private SituacaoCpf situacaoCpf;

    @JdbcTypeCode(SqlTypes.CHAR)
    private String cpf;

    @Embedded
    private Contato contato;

    @Embedded
    private Localizacao localizacao;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> ausencias;

    private Instant criadoEm;

    protected PessoaCadastro() {
    }

    public PessoaCadastro(
            String nome,
            String nomeSocial,
            LocalDate nascimento,
            SituacaoCpf situacaoCpf,
            String cpf,
            Contato contato,
            Localizacao localizacao,
            Map<String, String> ausencias,
            Instant criadoEm) {
        this.nome = nome;
        this.nomeSocial = nomeSocial;
        this.nascimento = nascimento;
        this.situacaoCpf = situacaoCpf;
        this.cpf = cpf;
        this.contato = contato;
        this.localizacao = localizacao;
        this.ausencias = Map.copyOf(ausencias);
        this.criadoEm = criadoEm;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getNomeSocial() {
        return nomeSocial;
    }

    public LocalDate getNascimento() {
        return nascimento;
    }

    public SituacaoCpf getSituacaoCpf() {
        return situacaoCpf;
    }

    public String getCpf() {
        return cpf;
    }

    public Contato getContato() {
        return contato;
    }

    public Localizacao getLocalizacao() {
        return localizacao;
    }

    public Map<String, String> getAusencias() {
        return ausencias;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
