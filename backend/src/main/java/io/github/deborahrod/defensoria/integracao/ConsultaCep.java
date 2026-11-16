package io.github.deborahrod.defensoria.integracao;

/** Porta de consulta de CEP. A única implementação de produção é {@link ServicoCep}. */
public interface ConsultaCep {

    /** @param cep oito dígitos, sem máscara */
    ResultadoCep consultar(String cep);
}
