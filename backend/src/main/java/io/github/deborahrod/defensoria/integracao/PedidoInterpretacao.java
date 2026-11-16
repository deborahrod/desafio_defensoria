package io.github.deborahrod.defensoria.integracao;

import java.util.List;

/**
 * O mínimo que o modelo recebe: a pergunta, o trecho respondido, os campos que pode propor e os
 * tópicos de ajuda que pode indicar. Nunca leva CPF, nascimento, telefone nem o histórico.
 */
public record PedidoInterpretacao(
        String pergunta, String resposta, List<CampoPermitido> campos, List<Topico> topicos) {

    /** Campo que o modelo pode propor. Se houver {@code opcoes}, o valor tem de ser um desses ids. */
    public record CampoPermitido(String id, String descricao, List<String> opcoes) {

        public CampoPermitido(String id, String descricao) {
            this(id, descricao, List.of());
        }
    }

    /** Verbete de ajuda que o modelo pode apontar pelo id. O texto exibido é sempre local. */
    public record Topico(String id, String descricao) {
    }

    public PedidoInterpretacao(String pergunta, String resposta, List<CampoPermitido> campos) {
        this(pergunta, resposta, campos, List.of());
    }
}
