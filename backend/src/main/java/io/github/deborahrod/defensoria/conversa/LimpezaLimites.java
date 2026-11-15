package io.github.deborahrod.defensoria.conversa;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class LimpezaLimites {

    private final LimitadorRequisicoes limitador;

    LimpezaLimites(LimitadorRequisicoes limitador) {
        this.limitador = limitador;
    }

    @Scheduled(fixedDelayString = "${cadastro.sessao.intervalo-limpeza}")
    void executar() {
        limitador.descartarInativos();
    }
}
