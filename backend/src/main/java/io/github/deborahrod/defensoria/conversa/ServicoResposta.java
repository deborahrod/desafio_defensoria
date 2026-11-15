package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.integracao.ServicoInterpretacao;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orquestra um turno de resposta. A chamada ao serviço de IA acontece aqui, entre duas transações
 * curtas, para não segurar conexão do banco enquanto se espera o provedor.
 */
@Service
public class ServicoResposta {

    private final ServicoConversa conversa;
    private final ServicoInterpretacao interpretacoes;

    public ServicoResposta(ServicoConversa conversa, ServicoInterpretacao interpretacoes) {
        this.conversa = conversa;
        this.interpretacoes = interpretacoes;
    }

    public EstadoConversa responder(UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada) {
        var interpretacao = conversa.prepararInterpretacao(sessaoId, mensagemId, versao, entrada)
                .map(interpretacoes::interpretar);
        // Se o estado mudou durante a espera, a versão enviada deixa de valer e o turno é recusado.
        return conversa.responder(sessaoId, mensagemId, versao, entrada, interpretacao);
    }
}
