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
    private final String detalhe;

    public FalhaInterpretacaoException(Categoria categoria) {
        this(categoria, null);
    }

    /** O detalhe nomeia a regra do contrato que falhou. Nunca carrega texto da pessoa nem da resposta do modelo. */
    public FalhaInterpretacaoException(Categoria categoria, String detalhe) {
        super(categoria.name());
        this.categoria = categoria;
        this.detalhe = detalhe;
    }

    public Categoria categoria() {
        return categoria;
    }

    public String detalhe() {
        return detalhe;
    }
}
