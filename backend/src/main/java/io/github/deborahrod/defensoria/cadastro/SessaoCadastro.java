package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessao_cadastro")
public class SessaoCadastro {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String segredoHash;

    @Enumerated(EnumType.STRING)
    private EstadoSessao estado;

    @Enumerated(EnumType.STRING)
    private ModoInteracao modoInteracao;

    @Version
    private int versao;

    private Instant criadoEm;
    private Instant ultimoAcessoEm;
    private Instant expiraEm;

    protected SessaoCadastro() {
    }

    public SessaoCadastro(String segredoHash, ModoInteracao modoInteracao, Instant agora, Duration inatividade) {
        this.segredoHash = segredoHash;
        this.estado = EstadoSessao.EM_PREENCHIMENTO;
        this.modoInteracao = modoInteracao;
        this.criadoEm = agora;
        registrarAcesso(agora, inatividade);
    }

    public void registrarAcesso(Instant agora, Duration inatividade) {
        this.ultimoAcessoEm = agora;
        this.expiraEm = agora.plus(inatividade);
    }

    public boolean expirouEm(Instant agora) {
        return !agora.isBefore(expiraEm);
    }

    public UUID getId() {
        return id;
    }

    public String getSegredoHash() {
        return segredoHash;
    }

    public EstadoSessao getEstado() {
        return estado;
    }

    public ModoInteracao getModoInteracao() {
        return modoInteracao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getUltimoAcessoEm() {
        return ultimoAcessoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }
}
