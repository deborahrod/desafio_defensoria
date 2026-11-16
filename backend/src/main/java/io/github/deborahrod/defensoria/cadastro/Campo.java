package io.github.deborahrod.defensoria.cadastro;

import java.util.Arrays;
import java.util.Optional;

public enum Campo {
    PARA_QUEM("paraQuem"),
    NOME_QUEM_PREENCHE("nomeQuemPreenche"),
    VINCULO_REPRESENTACAO("vinculoRepresentacao"),
    NOME("nome"),
    NOME_SOCIAL("nomeSocial"),
    NASCIMENTO("nascimento"),
    SITUACAO_CPF("situacaoCpf"),
    CPF("cpf"),
    FORMA_CONTATO("formaContato"),
    TELEFONE("telefone"),
    EMAIL("email"),
    REFERENCIA_NOME("referenciaNome"),
    REFERENCIA_VINCULO("referenciaVinculo"),
    REFERENCIA_CANAL("referenciaCanal"),
    AVISO_IA("avisoIa"),
    RELATO("relato"),
    MUNICIPIO("municipio"),
    UF("uf"),
    ENDERECO_FIXO("enderecoFixo"),
    LOGRADOURO("logradouro"),
    NUMERO("numero"),
    BAIRRO("bairro"),
    COMPLEMENTO("complemento"),
    CEP("cep"),
    REFERENCIA_LOCALIZACAO("referenciaLocalizacao"),
    OCUPACAO("ocupacao"),
    RENDA("renda"),
    QUANTIDADE_MEMBROS("quantidadeMembros"),
    DEPENDENTES("dependentes"),
    SITUACAO_MORADIA("situacaoMoradia");

    private final String id;

    Campo(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<Campo> deId(String id) {
        return Arrays.stream(values()).filter(campo -> campo.id.equals(id)).findFirst();
    }
}
