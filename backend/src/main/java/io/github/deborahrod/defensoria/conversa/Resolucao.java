package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;

/** O que uma resposta significa para o campo perguntado. */
public sealed interface Resolucao {

    record Valor(String valor) implements Resolucao {
    }

    record Ausente(MotivoAusencia motivo) implements Resolucao {
    }

    record Invalida(String mensagem) implements Resolucao {
    }
}
