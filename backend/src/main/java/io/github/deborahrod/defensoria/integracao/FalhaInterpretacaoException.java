package io.github.deborahrod.defensoria.integracao;

/** Falha da assistência por IA. Leva só a categoria: nunca a chave nem o conteúdo trocado. */
public class FalhaInterpretacaoException extends RuntimeException {

    public enum Categoria {
        SEM_CHAVE,
        NAO_AUTORIZADO,
        SEM_CREDITO,
        LIMITE_EXCEDIDO,
        TEMPO_ESGOTADO,
        ERRO_SERVIDOR,
        RESPOSTA_INVALIDA,
        INDISPONIVEL
    }

    private final Categoria categoria;

    public FalhaInterpretacaoException(Categoria categoria) {
        super(categoria.name());
        this.categoria = categoria;
    }

    public Categoria categoria() {
        return categoria;
    }
}
