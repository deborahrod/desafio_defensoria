package io.github.deborahrod.defensoria.integracao;

import java.util.Optional;

/** Um serviço público de CEP. */
interface ProvedorCep {

    String nome();

    /** Vazio quando o provedor responde que o CEP não existe. Falha de rede ou resposta fora do formato lança. */
    Optional<Endereco> buscar(String cep) throws FalhaCepException;
}
