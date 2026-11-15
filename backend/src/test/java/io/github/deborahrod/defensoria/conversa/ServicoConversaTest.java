package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.opcao;
import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.ServicoSessao;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.ItemResumo;
import io.github.deborahrod.defensoria.conversa.EstadoConversa.SituacaoItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ServicoConversaTest extends TesteIntegracao {

    @Autowired
    private ServicoSessao sessoes;

    @Autowired
    private ServicoConversa conversa;

    @Autowired
    private RascunhoCadastroRepository rascunhos;

    private UUID sessaoId;

    @BeforeEach
    void iniciarSessao() {
        sessaoId = sessoes.iniciar(ModoInteracao.ESTRUTURADO).sessao().getId();
    }

    @Test
    void conversaComecaPerguntandoParaQuemEOCadastro() {
        var estado = conversa.estadoAtual(sessaoId);

        assertThat(estado.estado()).isEqualTo(EstadoSessao.EM_PREENCHIMENTO);
        assertThat(estado.modoInteracao()).isEqualTo(ModoInteracao.ESTRUTURADO);
        assertThat(estado.proximaPergunta().campo()).isEqualTo("paraQuem");
        assertThat(estado.proximaPergunta().tipoEntrada()).isEqualTo(TipoEntrada.ESCOLHA);
        assertThat(estado.proximaPergunta().opcoes()).extracting(Opcao::id)
                .containsExactly("PROPRIA_PESSOA", "OUTRA_PESSOA");
        assertThat(estado.resumo()).isEmpty();
    }

    @Test
    void fluxoComumPercorreTodasAsPerguntasEChegaARevisao() {
        var estado = responderTudo(RespostasFicticias.fluxoComum());

        assertThat(estado.estado()).isEqualTo(EstadoSessao.EM_REVISAO);
        assertThat(estado.proximaPergunta()).isNull();
        assertThat(estado.progresso().secao()).isEqualTo("Revisão");
        assertThat(estado.pendencias()).isEmpty();
        assertThat(estado.resumo()).extracting(EstadoConversa.SecaoResumo::titulo).containsExactly(
                "Quem preenche", "Identificação", "Contato", "Localização", "Situação socioeconômica");
        assertThat(item(estado, "nome").valor()).isEqualTo("Maria Fictícia da Silva");
        assertThat(item(estado, "nascimento").valor()).isEqualTo("20/05/1990");
        assertThat(item(estado, "cpf").valor()).isEqualTo("529.982.247-25");
        assertThat(item(estado, "telefone").valor()).isEqualTo("(62) 91234-5678");
        assertThat(item(estado, "cep").valor()).isEqualTo("74000-000");
        assertThat(item(estado, "renda").valor()).isEqualTo("R$ 1.518,35");
        assertThat(item(estado, "situacaoMoradia").valor()).isEqualTo("Alugada");
        assertThat(item(estado, "nomeSocial").situacao()).isEqualTo(SituacaoItem.OPCIONAL_NAO_PREENCHIDO);
    }

    @Test
    void ausenciasFicamRegistradasComMotivoSemValorInventado() {
        var estado = responderTudo(RespostasFicticias.fluxoComAusencias());

        assertThat(estado.estado()).isEqualTo(EstadoSessao.EM_REVISAO);
        assertThat(item(estado, "nome").valor()).isEqualTo("Jurema");
        assertThat(item(estado, "nascimento").situacao()).isEqualTo(SituacaoItem.NAO_INFORMADO);
        assertThat(item(estado, "situacaoCpf").valor()).isEqualTo("Não possuo CPF");
        assertThat(item(estado, "renda").situacao()).isEqualTo(SituacaoItem.NAO_INFORMADO);
        assertThat(estado.pendencias()).contains(
                "Data de nascimento: Não soube informar",
                "CPF: Não possuo CPF",
                "Contato: não há contato disponível",
                "Referência de localização: Não disponível");

        var rascunho = rascunhos.findById(sessaoId).orElseThrow();
        assertThat(rascunho.valor(Campo.CPF)).isEmpty();
        assertThat(rascunho.valor(Campo.NASCIMENTO)).isEmpty();
        assertThat(rascunho.ausencia(Campo.NASCIMENTO)).contains(MotivoAusencia.DESCONHECIDO);
        assertThat(rascunho.valor(Campo.CEP)).isEmpty();
        assertThat(rascunho.valor(Campo.NUMERO)).isEmpty();
        assertThat(rascunho.valor(Campo.TELEFONE)).isEmpty();
    }

    @Test
    void cadastroParaOutraPessoaMantemQuemPreencheSeparadoDaTitular() {
        var estado = responderTudo(RespostasFicticias.fluxoParaOutraPessoa());

        assertThat(estado.estado()).isEqualTo(EstadoSessao.EM_REVISAO);
        assertThat(item(estado, "nomeQuemPreenche").valor()).isEqualTo("Rita Fictícia");
        assertThat(item(estado, "vinculoRepresentacao").valor()).isEqualTo("Sou familiar");
        assertThat(item(estado, "nome").valor()).isEqualTo("Antônio Fictício");
        assertThat(item(estado, "renda").valor()).isEqualTo("R$ 0,00 (não tenho renda)");
        assertThat(item(estado, "numero").valor()).isEqualTo("Sem número");
        assertThat(itens(estado)).extracting(ItemResumo::campo).doesNotContain("dependentes");
    }

    @Test
    void cpfInvalidoERecusadoSemAlterarORascunho() {
        responderTudo(RespostasFicticias.fluxoComum().subList(0, 5));
        var antes = conversa.estadoAtual(sessaoId);
        assertThat(antes.proximaPergunta().campo()).isEqualTo("cpf");

        assertThatThrownBy(() -> responder(valor("529.982.247-26")))
                .isInstanceOfSatisfying(RespostaInvalidaException.class,
                        erro -> assertThat(erro.getCampo()).isEqualTo(Campo.CPF.id()));

        var depois = conversa.estadoAtual(sessaoId);
        assertThat(depois.versao()).isEqualTo(antes.versao());
        assertThat(depois.proximaPergunta().campo()).isEqualTo("cpf");
    }

    @Test
    void nomeEmBrancoNaoAvanca() {
        responder(opcao("PROPRIA_PESSOA"));

        assertThatThrownBy(() -> responder(valor("   "))).isInstanceOf(RespostaInvalidaException.class);
        assertThat(conversa.estadoAtual(sessaoId).proximaPergunta().campo()).isEqualTo("nome");
    }

    @Test
    void respostaComVersaoAntigaNaoModificaOEstadoMaisRecente() {
        var inicial = conversa.estadoAtual(sessaoId);
        responder(opcao("PROPRIA_PESSOA"));

        assertThatThrownBy(() -> conversa.responder(sessaoId, UUID.randomUUID(), inicial.versao(), valor("Nome Atrasado")))
                .isInstanceOf(VersaoDesatualizadaException.class);

        assertThat(conversa.estadoAtual(sessaoId).proximaPergunta().campo()).isEqualTo("nome");
    }

    @Test
    void reenvioDaMesmaMensagemNaoEProcessadoDuasVezes() {
        var mensagemId = UUID.randomUUID();
        var versao = conversa.estadoAtual(sessaoId).versao();

        var primeira = conversa.responder(sessaoId, mensagemId, versao, opcao("PROPRIA_PESSOA"));
        var repetida = conversa.responder(sessaoId, mensagemId, versao, opcao("PROPRIA_PESSOA"));

        assertThat(repetida.versao()).isEqualTo(primeira.versao());
        assertThat(repetida.proximaPergunta().campo()).isEqualTo("nome");
    }

    @Test
    void cadaRespostaAceitaAvancaAVersaoEOProgresso() {
        var inicial = conversa.estadoAtual(sessaoId);
        var depois = responder(opcao("PROPRIA_PESSOA"));

        assertThat(depois.versao()).isGreaterThan(inicial.versao());
        assertThat(inicial.progresso().secao()).isEqualTo("Quem preenche");
        assertThat(depois.progresso().secao()).isEqualTo("Identificação");
        assertThat(depois.progresso().total()).isEqualTo(5);
    }

    private EstadoConversa responderTudo(List<RespostaEntrada> respostas) {
        EstadoConversa estado = null;
        for (var resposta : respostas) {
            estado = responder(resposta);
        }
        return estado;
    }

    private EstadoConversa responder(RespostaEntrada resposta) {
        var versao = conversa.estadoAtual(sessaoId).versao();
        return conversa.responder(sessaoId, UUID.randomUUID(), versao, resposta);
    }

    private static ItemResumo item(EstadoConversa estado, String campo) {
        return itens(estado).stream().filter(item -> item.campo().equals(campo)).findFirst().orElseThrow();
    }

    private static List<ItemResumo> itens(EstadoConversa estado) {
        return estado.resumo().stream().flatMap(secao -> secao.itens().stream()).toList();
    }
}
