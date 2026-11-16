package io.github.deborahrod.defensoria.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** https://brasilapi.com.br/api/cep/v2/{cep}, usado quando o ViaCEP falha ou não conhece o CEP. */
@Component
class ClienteBrasilApi extends ClienteHttpCep {

    ClienteBrasilApi(PropriedadesCep propriedades, ObjectMapper json) {
        super(propriedades.brasilapiUrl(), propriedades, json);
    }

    @Override
    public String nome() {
        return "brasilapi";
    }

    @Override
    public Optional<Endereco> buscar(String cep) throws FalhaCepException {
        var resposta = ler("/" + cep);
        if (resposta.isEmpty()) {
            return Optional.empty();
        }
        var no = resposta.get();
        return Optional.of(endereco(
                cep, texto(no, "street"), texto(no, "neighborhood"), texto(no, "city"), texto(no, "state")));
    }
}
