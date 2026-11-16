package io.github.deborahrod.defensoria.integracao;

/** Desfecho da consulta de CEP. Indisponível quer dizer que nenhum provedor respondeu: não prova que o CEP não existe. */
public sealed interface ResultadoCep {

    record Encontrado(Endereco endereco) implements ResultadoCep {
    }

    record NaoEncontrado() implements ResultadoCep {
    }

    record Indisponivel() implements ResultadoCep {
    }
}
