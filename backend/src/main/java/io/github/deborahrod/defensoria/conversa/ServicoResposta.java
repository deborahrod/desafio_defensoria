package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.integracao.ServicoInterpretacao;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orquestra um turno de resposta ou correção. A chamada ao serviço de IA acontece aqui, entre duas
 * transações curtas, para não segurar conexão do banco enquanto se espera o provedor.
 */
@Service
public class ServicoResposta {

    private final ServicoConversa conversa;
    private final ServicoInterpretacao interpretacoes;
    private final LimitadorRequisicoes limitador;

    public ServicoResposta(ServicoConversa conversa, ServicoInterpretacao interpretacoes, LimitadorRequisicoes limitador) {
        this.conversa = conversa;
        this.interpretacoes = interpretacoes;
        this.limitador = limitador;
    }

    public EstadoConversa responder(UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada) {
        try (var operacao = limitador.iniciarResposta(sessaoId)) {
            var interpretacao = conversa.prepararInterpretacao(sessaoId, mensagemId, versao, entrada)
                    .map(interpretacoes::interpretar);
            // Se o estado mudou durante a espera, a versão enviada deixa de valer e o turno é recusado.
            return conversa.responder(sessaoId, mensagemId, versao, entrada, interpretacao);
        }
    }

    public EstadoConversa corrigir(UUID sessaoId, String campo, int versao, RespostaEntrada entrada) {
        try (var operacao = limitador.iniciarResposta(sessaoId)) {
            return conversa.corrigir(sessaoId, campo, versao, entrada);
        }
    }
}
