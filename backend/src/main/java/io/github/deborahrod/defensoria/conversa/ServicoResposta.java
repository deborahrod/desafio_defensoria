package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.integracao.ConsultaCep;
import io.github.deborahrod.defensoria.integracao.ServicoInterpretacao;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Orquestra um turno de resposta ou correção. As chamadas ao serviço de IA e à consulta de CEP acontecem aqui, entre duas
 * transações curtas, para não segurar conexão do banco enquanto se espera o provedor.
 */
@Service
public class ServicoResposta {

    private final ServicoConversa conversa;
    private final ServicoInterpretacao interpretacoes;
    private final ConsultaCep ceps;
    private final LimitadorRequisicoes limitador;

    public ServicoResposta(
            ServicoConversa conversa,
            ServicoInterpretacao interpretacoes,
            ConsultaCep ceps,
            LimitadorRequisicoes limitador) {
        this.conversa = conversa;
        this.interpretacoes = interpretacoes;
        this.ceps = ceps;
        this.limitador = limitador;
    }

    public EstadoConversa responder(UUID sessaoId, UUID mensagemId, int versao, RespostaEntrada entrada) {
        try (var operacao = limitador.iniciarResposta(sessaoId)) {
            var interpretacao = conversa.prepararInterpretacao(sessaoId, mensagemId, versao, entrada)
                    .map(interpretacoes::interpretar);
            // Se o estado mudou durante a espera, a versão enviada deixa de valer e o turno é recusado.
            var endereco = conversa.prepararConsultaCep(sessaoId, mensagemId, versao, entrada).map(ceps::consultar);
            return conversa.responder(sessaoId, mensagemId, versao, entrada, interpretacao, endereco);
        }
    }

    public EstadoConversa corrigir(UUID sessaoId, String campo, int versao, RespostaEntrada entrada) {
        try (var operacao = limitador.iniciarResposta(sessaoId)) {
            return conversa.corrigir(sessaoId, campo, versao, entrada);
        }
    }
}
