package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.opcao;
import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.valor;

import java.util.List;

/** Roteiros de respostas com dados inventados, na ordem em que o servidor pergunta. */
public final class RespostasFicticias {

    public static final String CPF_VALIDO = "529.982.247-25";

    private RespostasFicticias() {
    }

    /** Cadastro para si, com CPF, telefone, endereço convencional e renda. */
    public static List<RespostaEntrada> fluxoComum() {
        return fluxoComumComCpf(CPF_VALIDO);
    }

    public static List<RespostaEntrada> fluxoComumComCpf(String cpf) {
        return List.of(
                opcao("PROPRIA_PESSOA"),
                valor("Maria Fictícia da Silva"),
                opcao("PULAR"),
                valor("20/05/1990"),
                opcao("INFORMADO"),
                valor(cpf),
                opcao("TELEFONE"),
                valor("(62) 91234-5678"),
                opcao("PULAR"),
                valor("Goiânia"),
                opcao("GO"),
                opcao("SIM"),
                valor("Rua das Flores Inventadas"),
                valor("123"),
                valor("Setor Central"),
                opcao("PULAR"),
                valor("74000-000"),
                valor("Diarista"),
                valor("1.518,35"),
                valor("3"),
                valor("2"),
                opcao("ALUGADA"));
    }

    /** Nome único, sem data, sem CPF, sem contato, sem endereço fixo e sem renda conhecida. */
    public static List<RespostaEntrada> fluxoComAusencias() {
        return List.of(
                opcao("PROPRIA_PESSOA"),
                valor("Jurema"),
                opcao("PULAR"),
                opcao("NAO_SEI"),
                opcao("NAO_POSSUI"),
                opcao("INDISPONIVEL"),
                opcao("NAO_SEI"),
                opcao("NAO"),
                opcao("SEM_REFERENCIA"),
                opcao("NAO_SEI"),
                opcao("NAO_SEI"),
                opcao("NAO_SEI"),
                opcao("NAO_SEI"),
                opcao("SEM_ENDERECO_FIXO"));
    }

    /** Cadastro preenchido por familiar, com contato por pessoa de referência. */
    public static List<RespostaEntrada> fluxoParaOutraPessoa() {
        return List.of(
                opcao("OUTRA_PESSOA"),
                valor("Rita Fictícia"),
                opcao("FAMILIAR"),
                valor("Antônio Fictício"),
                opcao("PULAR"),
                valor("03/02/1948"),
                opcao("NAO_APRESENTADO"),
                opcao("REFERENCIA"),
                valor("Rita Fictícia"),
                valor("filha"),
                valor("rita@exemplo.com"),
                valor("Anápolis"),
                opcao("GO"),
                opcao("SIM"),
                valor("Avenida Imaginária"),
                opcao("SEM_NUMERO"),
                opcao("NAO_SEI"),
                opcao("PULAR"),
                opcao("PULAR"),
                valor("Aposentado"),
                opcao("SEM_RENDA"),
                valor("1"),
                opcao("CEDIDA"));
    }
}
