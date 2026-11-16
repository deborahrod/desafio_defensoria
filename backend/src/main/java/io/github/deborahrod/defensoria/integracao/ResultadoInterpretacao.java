package io.github.deborahrod.defensoria.integracao;

import java.util.List;

/** Proposta do modelo, já conferida contra o contrato. Ainda precisa passar pelas regras do domínio. */
public record ResultadoInterpretacao(
        Operacao operacao, List<Candidato> candidatos, boolean precisaEsclarecimento, String topico) {

    /** O modelo nunca devolve uma operação que confirme ou grave o cadastro. */
    public enum Operacao {
        PROPOR_VALORES,
        SOLICITAR_ESCLARECIMENTO,
        /** Indica um tópico do catálogo de ajuda. Extensão do contrato original da ata. */
        AJUDA,
        FORA_DE_ESCOPO
    }

    public record Candidato(String campo, String valor, String trechoOrigem) {
    }

    public ResultadoInterpretacao(Operacao operacao, List<Candidato> candidatos, boolean precisaEsclarecimento) {
        this(operacao, candidatos, precisaEsclarecimento, null);
    }
}
