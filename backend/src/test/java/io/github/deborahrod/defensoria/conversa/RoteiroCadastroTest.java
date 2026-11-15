package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.cadastro.Campo.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoteiroCadastroTest {

    private static final Instant AGORA = Instant.parse("2026-11-15T12:00:00Z");

    private final RoteiroCadastro roteiro = new RoteiroCadastro();
    private final RascunhoCadastro rascunho = new RascunhoCadastro(UUID.randomUUID(), AGORA);

    @Test
    void comecaPerguntandoParaQuemEOCadastro() {
        assertThat(proxima()).isEqualTo(PARA_QUEM);
    }

    @Test
    void cadastroParaSiNaoPerguntaSobreQuemPreenche() {
        rascunho.informar(PARA_QUEM, RoteiroCadastro.PROPRIA_PESSOA, AGORA);

        assertThat(proxima()).isEqualTo(NOME);
        assertThat(roteiro.proxima(rascunho).orElseThrow().texto(false)).isEqualTo("Como você se chama?");
    }

    @Test
    void cadastroParaOutraPessoaColetaQuemPreencheEMudaORedacaoDasPerguntas() {
        rascunho.informar(PARA_QUEM, RoteiroCadastro.OUTRA_PESSOA, AGORA);
        assertThat(proxima()).isEqualTo(NOME_QUEM_PREENCHE);

        rascunho.informar(NOME_QUEM_PREENCHE, "Rita Fictícia", AGORA);
        assertThat(proxima()).isEqualTo(VINCULO_REPRESENTACAO);

        rascunho.informar(VINCULO_REPRESENTACAO, "FAMILIAR", AGORA);
        var pergunta = roteiro.proxima(rascunho).orElseThrow();
        assertThat(pergunta.campo()).isEqualTo(NOME);
        assertThat(pergunta.texto(RoteiroCadastro.paraOutraPessoa(rascunho)))
                .isEqualTo("Como se chama a pessoa que será atendida?");
    }

    @Test
    void cpfSoEPedidoQuandoAPessoaDizQueVaiInformar() {
        responderAte(SITUACAO_CPF);
        rascunho.informar(SITUACAO_CPF, "NAO_POSSUI", AGORA);
        assertThat(proxima()).isEqualTo(FORMA_CONTATO);

        rascunho.informar(SITUACAO_CPF, "INFORMADO", AGORA);
        assertThat(proxima()).isEqualTo(CPF);
    }

    @Test
    void contatoPorOutraPessoaPedeNomeRelacaoECanalSemCpf() {
        responderAte(FORMA_CONTATO);
        rascunho.informar(FORMA_CONTATO, "REFERENCIA", AGORA);

        assertThat(proxima()).isEqualTo(REFERENCIA_NOME);
        rascunho.informar(REFERENCIA_NOME, "Vizinha Fictícia", AGORA);
        assertThat(proxima()).isEqualTo(REFERENCIA_VINCULO);
        rascunho.informar(REFERENCIA_VINCULO, "vizinha", AGORA);
        assertThat(proxima()).isEqualTo(REFERENCIA_CANAL);
        rascunho.informar(REFERENCIA_CANAL, "62912345678", AGORA);
        assertThat(proxima()).isEqualTo(MUNICIPIO);
    }

    @Test
    void semContatoDisponivelSegueDiretoParaALocalizacao() {
        responderAte(FORMA_CONTATO);
        rascunho.informar(FORMA_CONTATO, "INDISPONIVEL", AGORA);

        assertThat(proxima()).isEqualTo(MUNICIPIO);
    }

    @Test
    void emailEObrigatorioSeForOContatoEOpcionalAposOTelefone() {
        responderAte(FORMA_CONTATO);
        rascunho.informar(FORMA_CONTATO, "EMAIL", AGORA);
        assertThat(roteiro.proxima(rascunho).orElseThrow().opcional()).isFalse();

        rascunho.informar(FORMA_CONTATO, "TELEFONE", AGORA);
        rascunho.informar(TELEFONE, "62912345678", AGORA);
        var email = roteiro.proxima(rascunho).orElseThrow();
        assertThat(email.campo()).isEqualTo(EMAIL);
        assertThat(email.opcional()).isTrue();
    }

    @Test
    void semEnderecoFixoPedeReferenciaENaoExigeCepNemNumero() {
        responderAte(ENDERECO_FIXO);
        rascunho.informar(ENDERECO_FIXO, RoteiroCadastro.NAO, AGORA);

        assertThat(proxima()).isEqualTo(REFERENCIA_LOCALIZACAO);
        rascunho.registrarAusencia(REFERENCIA_LOCALIZACAO, MotivoAusencia.INDISPONIVEL, AGORA);
        assertThat(proxima()).isEqualTo(OCUPACAO);
        assertThat(roteiro.de(CEP, rascunho)).isEmpty();
        assertThat(roteiro.de(NUMERO, rascunho)).isEmpty();
    }

    @Test
    void municipioDesconhecidoNaoPerguntaOEstado() {
        responderAte(MUNICIPIO);
        rascunho.registrarAusencia(MUNICIPIO, MotivoAusencia.DESCONHECIDO, AGORA);

        assertThat(proxima()).isEqualTo(ENDERECO_FIXO);
    }

    @Test
    void opcoesDeMoradiaDependemDeHaverEnderecoFixo() {
        rascunho.informar(ENDERECO_FIXO, RoteiroCadastro.SIM, AGORA);
        assertThat(opcoesDeMoradia()).contains("PROPRIA", "ALUGADA").doesNotContain("SEM_ENDERECO_FIXO");

        rascunho.informar(ENDERECO_FIXO, RoteiroCadastro.NAO, AGORA);
        assertThat(opcoesDeMoradia()).contains("SEM_ENDERECO_FIXO", "ACOLHIMENTO").doesNotContain("PROPRIA");
    }

    @Test
    void quemMoraSozinhoNaoRespondeSobreDependentes() {
        rascunho.informar(QUANTIDADE_MEMBROS, "1", AGORA);
        assertThat(roteiro.de(DEPENDENTES, rascunho)).isEmpty();

        rascunho.informar(QUANTIDADE_MEMBROS, "4", AGORA);
        assertThat(roteiro.de(DEPENDENTES, rascunho)).isPresent();
    }

    @Test
    void rendaZeroEValorEDesconhecimentoEAusencia() {
        var renda = roteiro.de(RENDA, rascunho).orElseThrow();
        var contexto = new ContextoResposta(rascunho, LocalDate.of(2026, 11, 15));

        assertThat(renda.resolver(RespostaEntrada.opcao("SEM_RENDA"), contexto))
                .isEqualTo(new Resolucao.Valor("0.00"));
        assertThat(renda.resolver(RespostaEntrada.opcao("NAO_SEI"), contexto))
                .isEqualTo(new Resolucao.Ausente(MotivoAusencia.DESCONHECIDO));
        assertThat(renda.exibir("0.00", rascunho)).isEqualTo("R$ 0,00 (não tenho renda)");
        assertThat(renda.exibir("1518.35", rascunho)).isEqualTo("R$ 1.518,35");
    }

    @Test
    void perguntaDeEscolhaNaoAceitaTextoLivreNemOpcaoDesconhecida() {
        var paraQuem = roteiro.proxima(rascunho).orElseThrow();
        var contexto = new ContextoResposta(rascunho, LocalDate.of(2026, 11, 15));

        assertThat(paraQuem.resolver(RespostaEntrada.valor("para mim"), contexto)).isInstanceOf(Resolucao.Invalida.class);
        assertThat(paraQuem.resolver(RespostaEntrada.opcao("ADMIN"), contexto)).isInstanceOf(Resolucao.Invalida.class);
    }

    @Test
    void semPerguntasPendentesORoteiroTermina() {
        responderAte(null);

        assertThat(roteiro.proxima(rascunho)).isEmpty();
    }

    private Campo proxima() {
        return roteiro.proxima(rascunho).orElseThrow().campo();
    }

    private java.util.List<String> opcoesDeMoradia() {
        return roteiro.de(SITUACAO_MORADIA, rascunho).orElseThrow().opcoes(rascunho).stream().map(Opcao::id).toList();
    }

    /** Responde com ausência genérica até chegar ao campo pedido, sem passar por ele. */
    private void responderAte(Campo destino) {
        while (true) {
            var pergunta = roteiro.proxima(rascunho);
            if (pergunta.isEmpty() || pergunta.get().campo() == destino) {
                return;
            }
            var campo = pergunta.get().campo();
            if (campo == PARA_QUEM) {
                rascunho.informar(campo, RoteiroCadastro.PROPRIA_PESSOA, AGORA);
            } else {
                rascunho.registrarAusencia(campo, MotivoAusencia.DESCONHECIDO, AGORA);
            }
        }
    }
}
