package io.github.deborahrod.defensoria.cadastro;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rascunho_cadastro")
public class RascunhoCadastro {

    @Id
    private UUID sessaoId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> dados = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> ausencias = new LinkedHashMap<>();

    private int versao;

    private Instant atualizadoEm;

    protected RascunhoCadastro() {
    }

    public RascunhoCadastro(UUID sessaoId, Instant agora) {
        this.sessaoId = sessaoId;
        this.atualizadoEm = agora;
    }

    public void informar(Campo campo, String valor, Instant agora) {
        var novosDados = new LinkedHashMap<>(dados);
        novosDados.put(campo.id(), valor);
        var novasAusencias = new LinkedHashMap<>(ausencias);
        novasAusencias.remove(campo.id());
        aplicar(novosDados, novasAusencias, agora);
    }

    public void registrarAusencia(Campo campo, MotivoAusencia motivo, Instant agora) {
        var novosDados = new LinkedHashMap<>(dados);
        novosDados.remove(campo.id());
        var novasAusencias = new LinkedHashMap<>(ausencias);
        novasAusencias.put(campo.id(), motivo.name());
        aplicar(novosDados, novasAusencias, agora);
    }

    public void limpar(Campo campo, Instant agora) {
        if (!respondido(campo)) {
            return;
        }
        var novosDados = new LinkedHashMap<>(dados);
        novosDados.remove(campo.id());
        var novasAusencias = new LinkedHashMap<>(ausencias);
        novasAusencias.remove(campo.id());
        aplicar(novosDados, novasAusencias, agora);
    }

    public Optional<String> valor(Campo campo) {
        return Optional.ofNullable(dados.get(campo.id()));
    }

    public Optional<MotivoAusencia> ausencia(Campo campo) {
        return Optional.ofNullable(ausencias.get(campo.id())).map(MotivoAusencia::valueOf);
    }

    public boolean respondido(Campo campo) {
        return dados.containsKey(campo.id()) || ausencias.containsKey(campo.id());
    }

    public UUID getSessaoId() {
        return sessaoId;
    }

    public int getVersao() {
        return versao;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    // Os mapas são substituídos, nunca alterados no lugar, para o Hibernate perceber a mudança no JSONB.
    private void aplicar(Map<String, String> novosDados, Map<String, String> novasAusencias, Instant agora) {
        this.dados = novosDados;
        this.ausencias = novasAusencias;
        this.versao++;
        this.atualizadoEm = agora;
    }
}
