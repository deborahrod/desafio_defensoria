package io.github.deborahrod.defensoria;

import io.github.deborahrod.defensoria.integracao.ConsultaCep;
import io.github.deborahrod.defensoria.integracao.Endereco;
import io.github.deborahrod.defensoria.integracao.ResultadoCep;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Consulta de CEP programável dos testes. Sem programação, os provedores estão fora do ar. */
public class ConsultaCepFalsa implements ConsultaCep {

    public final List<String> consultas = new CopyOnWriteArrayList<>();
    private volatile ResultadoCep resultado = new ResultadoCep.Indisponivel();

    public void reiniciar() {
        consultas.clear();
        resultado = new ResultadoCep.Indisponivel();
    }

    public void achar(String logradouro, String bairro, String municipio, String uf) {
        resultado = new ResultadoCep.Encontrado(new Endereco("74000000", logradouro, bairro, municipio, uf));
    }

    public void naoEncontrar() {
        resultado = new ResultadoCep.NaoEncontrado();
    }

    public void ficarForaDoAr() {
        resultado = new ResultadoCep.Indisponivel();
    }

    @Override
    public ResultadoCep consultar(String cep) {
        consultas.add(cep);
        return resultado;
    }
}
