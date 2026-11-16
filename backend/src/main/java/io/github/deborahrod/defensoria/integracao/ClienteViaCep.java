package io.github.deborahrod.defensoria.integracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** https://viacep.com.br/ws/{cep}/json/ */
@Component
class ClienteViaCep extends ClienteHttpCep {

    ClienteViaCep(PropriedadesCep propriedades, ObjectMapper json) {
        super(propriedades.viacepUrl(), propriedades, json);
    }

    @Override
    public String nome() {
        return "viacep";
    }

    @Override
    public Optional<Endereco> buscar(String cep) throws FalhaCepException {
        var resposta = ler("/" + cep + "/json/");
        if (resposta.isEmpty() || resposta.get().has("erro")) {
            return Optional.empty();
        }
        var no = resposta.get();
        return Optional.of(endereco(
                cep, texto(no, "logradouro"), texto(no, "bairro"), texto(no, "localidade"), texto(no, "uf")));
    }
}
