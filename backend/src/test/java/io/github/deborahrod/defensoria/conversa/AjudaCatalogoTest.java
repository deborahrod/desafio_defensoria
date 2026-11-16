package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.Topico;
import org.junit.jupiter.api.Test;

class AjudaCatalogoTest {

    private final AjudaCatalogo catalogo = new AjudaCatalogo();

    @Test
    void todoTopicoTemDescricaoParaOModeloETextoLocalParaAPessoa() {
        assertThat(catalogo.topicos()).hasSizeGreaterThanOrEqualTo(10);
        for (var topico : catalogo.topicos()) {
            assertThat(topico.descricao()).isNotBlank();
            assertThat(catalogo.texto(topico.id())).hasValueSatisfying(texto -> assertThat(texto).hasSizeGreaterThan(40));
        }
        assertThat(catalogo.topicos()).extracting(Topico::id).doesNotHaveDuplicates();
    }

    @Test
    void idDesconhecidoOuNuloNaoTemTexto() {
        assertThat(catalogo.texto("inventado")).isEmpty();
        assertThat(catalogo.texto(null)).isEmpty();
    }

    @Test
    void osVerbetesNaoPrometemAtendimentoNemDaoParecerJuridico() {
        for (var topico : catalogo.topicos()) {
            var texto = catalogo.texto(topico.id()).orElseThrow().toLowerCase();
            assertThat(texto).doesNotContain("garant").doesNotContain("tem direito").doesNotContain("vai ser atendid");
        }
    }
}
