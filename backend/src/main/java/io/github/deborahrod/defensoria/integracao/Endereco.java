package io.github.deborahrod.defensoria.integracao;

/** Endereço devolvido pela consulta de CEP. Logradouro e bairro ficam vazios em CEP de município pequeno. */
public record Endereco(String cep, String logradouro, String bairro, String municipio, String uf) {
}
