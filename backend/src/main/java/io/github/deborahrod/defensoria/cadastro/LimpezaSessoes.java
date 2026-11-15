package io.github.deborahrod.defensoria.cadastro;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class LimpezaSessoes {

    private final ServicoSessao sessoes;

    LimpezaSessoes(ServicoSessao sessoes) {
        this.sessoes = sessoes;
    }

    @Scheduled(fixedDelayString = "${cadastro.sessao.intervalo-limpeza}")
    void executar() {
        sessoes.limparExpiradas();
    }
}
