package io.github.deborahrod.defensoria.conversa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de uso aplicados no backend. Os números são hipóteses iniciais, não capacidade medida.
 * O limite por origem só olha o cabeçalho X-Forwarded-For quando há um proxy confiável na frente.
 */
@ConfigurationProperties("cadastro.limites")
public record PropriedadesLimites(
        int respostasPorSessao, int respostasPorMinuto, int requisicoesPorOrigemPorMinuto, boolean confiarEmProxy) {

    public PropriedadesLimites {
        respostasPorSessao = respostasPorSessao <= 0 ? 60 : respostasPorSessao;
        respostasPorMinuto = respostasPorMinuto <= 0 ? 10 : respostasPorMinuto;
        requisicoesPorOrigemPorMinuto = requisicoesPorOrigemPorMinuto <= 0 ? 120 : requisicoesPorOrigemPorMinuto;
    }
}
