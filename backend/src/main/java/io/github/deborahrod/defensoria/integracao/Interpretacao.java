package io.github.deborahrod.defensoria.integracao;

/** Desfecho de uma tentativa de interpretação: proposta do modelo ou falha categorizada. */
public sealed interface Interpretacao {

    record Sucesso(ResultadoInterpretacao resultado) implements Interpretacao {
    }

    record Falha(FalhaInterpretacaoException.Categoria categoria) implements Interpretacao {
    }
}
