package io.github.deborahrod.defensoria.integracao;

import java.util.List;

/** O mínimo que o modelo recebe: a pergunta, o trecho respondido e os campos que pode propor. */
public record PedidoInterpretacao(String pergunta, String resposta, List<CampoPermitido> campos) {

    public record CampoPermitido(String id, String descricao) {
    }
}
