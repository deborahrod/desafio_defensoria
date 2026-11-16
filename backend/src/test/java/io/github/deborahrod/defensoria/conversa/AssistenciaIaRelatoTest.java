package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.opcao;
import static io.github.deborahrod.defensoria.conversa.RespostaEntrada.valor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import io.github.deborahrod.defensoria.integracao.FalhaInterpretacaoException.Categoria;
import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.CampoPermitido;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Relato livre, normalização com confirmação e ajuda por catálogo. A IA é programada nos testes. */
@TestPropertySource(properties = "cadastro.llm.api-key=chave-de-teste")
class AssistenciaIaRelatoTest extends TesteIntegracao {

    private static final String RELATO =
            "Moro em Anápolis, alugado, com minha mãe e dois filhos. Faço bico de pedreiro, uns mil e quinhentos por mês.";

    private static final List<RespostaEntrada> ATE_O_AVISO = List.of(
            opcao("PROPRIA_PESSOA"), valor("Ana Fictícia"), opcao("PULAR"), opcao("NAO_SEI"),
            opcao("NAO_POSSUI"), opcao("INDISPONIVEL"));

    @Autowired
    private RascunhoCadastroRepository rascunhos;

    @Autowired
    private SessaoCadastroRepository sessoes;

    @Autowired
    private ServicoConversa servicoConversa;

    private ParticipanteApi noRelato() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        participante.responderTudo(ATE_O_AVISO);
        participante.responderOpcao("USAR_IA").andExpect(status().isOk());
        return participante;
    }

    private ParticipanteApi noMunicipio() throws Exception {
        var participante = noRelato();
        participante.responderOpcao("PULAR_RELATO").andExpect(status().isOk());
        return participante;
    }

    /** Responde o município (eco do digitado) e segue até a pergunta de ocupação. */
    private ParticipanteApi naOcupacao() throws Exception {
        var participante = noMunicipio();
        interpretador.propor("municipio", "Goiânia", "Goiânia");
        participante.responderValor("Goiânia").andExpect(status().isOk());
        participante.responderOpcao("GO");
        participante.responderOpcao("NAO");
        participante.responderOpcao("SEM_REFERENCIA");
        return participante;
    }

    private void proporRelatoCompleto() {
        interpretador.propor(
                "municipio", "Anápolis", "Anápolis",
                "uf", "GO", "Anápolis",
                "ocupacao", "Pedreiro (trabalho informal)", "bico de pedreiro",
                "renda", "1500.00", "mil e quinhentos",
                "quantidadeMembros", "4", "minha mãe e dois filhos",
                "dependentes", "2", "dois filhos",
                "situacaoMoradia", "ALUGADA", "alugado");
    }

    // ---- Relato

    @Test
    void relatoComVariosDadosVaiParaConfirmacaoComOsItensFormatados() throws Exception {
        var participante = noRelato();
        proporRelatoCompleto();

        participante.responderValor(RELATO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.itens.length()").value(7))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Município ou localidade')].valor").value("Anápolis"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Estado')].valor").value("GO"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Ocupação')].valor").value("Pedreiro (trabalho informal)"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Renda mensal declarada')].valor").value("R$ 1.500,00"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Pessoas na mesma casa')].valor").value("4"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Dependentes')].valor").value("2"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Moradia')].valor").value("Alugada"))
                .andExpect(jsonPath("$.proximaPergunta.texto").value(startsWith("Entendi assim:")))
                .andExpect(jsonPath("$.proximaPergunta.opcoes[0].rotulo").value("Sim, está certo"));
        // Nada entra no rascunho antes da confirmação.
        participante.navegador.get("/atual").andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='ocupacao')]").isEmpty());
    }

    @Test
    void oPedidoDoRelatoLevaOsCamposPermitidosComCatalogoETopicosEMaisNada() throws Exception {
        var participante = noRelato();
        proporRelatoCompleto();

        participante.responderValor(RELATO).andExpect(status().isOk());

        var pedido = interpretador.pedidos.getFirst();
        assertThat(pedido.resposta()).isEqualTo(RELATO);
        assertThat(pedido.campos()).extracting(CampoPermitido::id).containsExactly(
                "municipio", "uf", "ocupacao", "renda", "quantidadeMembros", "dependentes", "situacaoMoradia");
        var moradia = pedido.campos().stream().filter(c -> c.id().equals("situacaoMoradia")).findFirst().orElseThrow();
        assertThat(moradia.opcoes()).contains("PROPRIA", "ALUGADA", "CEDIDA").doesNotContain("NAO_SEI");
        var uf = pedido.campos().stream().filter(c -> c.id().equals("uf")).findFirst().orElseThrow();
        assertThat(uf.opcoes()).hasSize(27).contains("GO");
        assertThat(pedido.topicos()).isNotEmpty();
        assertThat(pedido.toString()).doesNotContain("Ana Fictícia").doesNotContain("chave-de-teste");
    }

    @Test
    void confirmarAplicaTudoPulaAsPerguntasJaCobertasENaoGuardaOTextoDoRelato() throws Exception {
        var participante = noRelato();
        proporRelatoCompleto();
        participante.responderValor(RELATO);

        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("enderecoFixo"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='municipio')].valor").value("Anápolis"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='ocupacao')].valor").value("Pedreiro (trabalho informal)"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='relato')]").isEmpty());

        var rascunho = rascunhos.findAll().getFirst();
        assertThat(rascunho.valor(Campo.RELATO)).contains("ENVIADO");
        assertThat(rascunho.toString()).doesNotContain("mil e quinhentos");
        assertThat(rascunhos.findAll().getFirst().valor(Campo.OCUPACAO)).contains("Pedreiro (trabalho informal)");
    }

    @Test
    void moradiaVindaDoRelatoQueDeixaDeValerComSemEnderecoFixoVoltaASerPerguntada() throws Exception {
        var participante = noRelato();
        proporRelatoCompleto();
        participante.responderValor(RELATO);
        participante.responderOpcao("CONFIRMAR");

        participante.responderOpcao("NAO")
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='situacaoMoradia')]").isEmpty())
                .andExpect(jsonPath("$.proximaPergunta.campo").value("referenciaLocalizacao"));
        participante.responderOpcao("SEM_REFERENCIA")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("situacaoMoradia"))
                .andExpect(jsonPath("$.proximaPergunta.opcoes[*].id").value(org.hamcrest.Matchers.hasItem("SEM_ENDERECO_FIXO")));
    }

    @Test
    void moradiaVindaDoRelatoPermaneceQuandoHaEnderecoFixo() throws Exception {
        var participante = noRelato();
        proporRelatoCompleto();
        participante.responderValor(RELATO);
        participante.responderOpcao("CONFIRMAR");

        participante.responderOpcao("SIM")
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='situacaoMoradia')].valor").value("Alugada"));
    }

    @Test
    void recusarARecolhaDoRelatoNaoAplicaNadaEPerguntaOQueFazer() throws Exception {
        var participante = noRelato();
        proporRelatoCompleto();
        participante.responderValor(RELATO);

        participante.responderOpcao("MANTER")
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("relato"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='municipio')]").isEmpty());
        participante.responderOpcao("PULAR_RELATO")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("municipio"));
    }

    @Test
    void dependentesNaoPodemSerMaisQueAsPessoasDaMesmaCasaMesmoNoMesmoRelato() throws Exception {
        var participante = noRelato();
        interpretador.propor("quantidadeMembros", "2", "dois", "dependentes", "3", "três");

        participante.responderValor("Somos dois em casa e três dependem de mim")
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.itens.length()").value(1))
                .andExpect(jsonPath("$.proximaPergunta.itens[0].rotulo").value("Pessoas na mesma casa"));
    }

    @Test
    void valorForaDoCatalogoOuUfInvalidaSaoDescartadosEORestoFica() throws Exception {
        var participante = noRelato();
        interpretador.propor(
                "municipio", "Anápolis", "Anápolis", "uf", "ZZ", "Anápolis", "situacaoMoradia", "SEM_ENDERECO_FIXO", "alugado",
                "renda", "muito dinheiro", "muito dinheiro");

        participante.responderValor("Moro em Anápolis, alugado, ganho muito dinheiro")
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.itens.length()").value(1))
                .andExpect(jsonPath("$.proximaPergunta.itens[0].valor").value("Anápolis"));
    }

    @Test
    void relatoSemDadosUteisVoltaComMensagemCompreensivelEOEstadoNaoMuda() throws Exception {
        var participante = noRelato();
        interpretador.propor();
        var versao = participante.versao;

        participante.responderValor("Preciso de ajuda")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("relato"))
                .andExpect(jsonPath("$.mensagem").value(containsString("Não consegui identificar dados")));

        participante.navegador.get("/atual").andExpect(jsonPath("$.versao").value(versao));
    }

    @Test
    void relatoComDetalhesDoCasoNaoVaiParaOModelo() throws Exception {
        for (var texto : List.of("Sofro violência em casa", "Fui presa e perdi o emprego", "Tenho depressão, moro em Anápolis")) {
            var participante = noRelato();

            participante.responderValor(texto)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensagem").value(containsString("não conte detalhes do seu caso")));
        }
        assertThat(interpretador.pedidos).isEmpty();
    }

    @Test
    void relatoComDadosPessoaisOuMaiorQueOLimiteNaoVaiParaOModelo() throws Exception {
        var participante = noRelato();

        participante.responderValor("Meu CPF é 529.982.247-25").andExpect(status().isBadRequest());
        participante.responderValor("Me ligue no (62) 91234-5678").andExpect(status().isBadRequest());
        participante.responderValor("casa ".repeat(81))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("até 400 caracteres")));

        assertThat(interpretador.pedidos).isEmpty();
    }

    @Test
    void falhaDaIaNoRelatoPassaAPerguntasDiretasPulaORelatoEPreservaOsDados() throws Exception {
        var participante = noRelato();
        interpretador.falharCom(Categoria.TEMPO_ESGOTADO);

        participante.responderValor(RELATO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoInteracao").value("ESTRUTURADO"))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("indisponível")))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("municipio"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='nome')].valor").value("Ana Fictícia"));

        assertThat(rascunhos.findAll().getFirst().ausencia(Campo.RELATO)).contains(MotivoAusencia.OPCIONAL_NAO_PREENCHIDO);
        participante.responderValor("Anápolis").andExpect(jsonPath("$.proximaPergunta.campo").value("uf"));
        assertThat(interpretador.pedidos).hasSize(1);
    }

    @Test
    void semAIaOuComPerguntasEstruturadasOrelatoNuncaApareceENaoHaChamada() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar();
        participante.responderTudo(ATE_O_AVISO);

        participante.responderOpcao("ESTRUTURADO")
                .andExpect(jsonPath("$.modoInteracao").value("ESTRUTURADO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("municipio"));
        assertThat(interpretador.pedidos).isEmpty();
    }

    @Test
    void textoDoRelatoNuncaViraValorDoRascunhoSeAInterpretacaoNaoChegar() throws Exception {
        var participante = noRelato();
        var sessaoId = sessoes.findAll().getFirst().getId();

        // Chamada ao serviço sem interpretação, como se a IA não tivesse sido consultada.
        assertThatThrownBy(() -> servicoConversa.responder(sessaoId, UUID.randomUUID(), participante.versao, valor(RELATO)))
                .isInstanceOfSatisfying(RespostaInvalidaException.class, erro -> {
                    assertThat(erro.getCampo()).isEqualTo("relato");
                    assertThat(erro.getMessage()).contains("Responda pergunta por pergunta");
                });

        assertThat(rascunhos.findAll().getFirst().valor(Campo.RELATO)).isEmpty();
    }

    // ---- Normalização com confirmação

    @Test
    void municipioDigitadoComErroENormalizadoEPedeConfirmacaoComUfSugerida() throws Exception {
        var participante = noMunicipio();
        interpretador.propor("municipio", "Aparecida de Goiânia", "apda de goiania", "uf", "GO", "apda de goiania");

        participante.responderValor("apda de goiania")
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Município ou localidade')].valor").value("Aparecida de Goiânia"))
                .andExpect(jsonPath("$.proximaPergunta.itens[?(@.rotulo=='Estado')].valor").value("GO"));
        participante.navegador.get("/atual").andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='municipio')]").isEmpty());

        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='municipio')].valor").value("Aparecida de Goiânia"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='uf')].valor").value("GO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("enderecoFixo"));
    }

    @Test
    void municipioIgualAoDigitadoSemUfSugeridaSegueParaAPerguntaDoEstado() throws Exception {
        var participante = noMunicipio();
        // Cidade homônima em vários estados: o modelo não sugere UF e o servidor pergunta.
        interpretador.propor("municipio", "Santa Helena", "santa helena");

        participante.responderValor("santa helena")
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("uf"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='municipio')].valor").value("Santa Helena"));
    }

    @Test
    void ufInvalidaSugeridaPeloModeloEDescartadaESeguePerguntandoOEstado() throws Exception {
        var participante = noMunicipio();
        interpretador.propor("municipio", "Goiânia", "Goiânia", "uf", "ZZ", "Goiânia");

        participante.responderValor("Goiânia")
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("uf"));
    }

    @Test
    void ocupacaoReescritaPeloModeloPedeConfirmacao() throws Exception {
        var participante = naOcupacao();
        interpretador.propor("ocupacao", "Pedreiro (trabalho informal)", "bico de pedreiro");

        participante.responderValor("faço bico de pedreiro")
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.itens[0].valor").value("Pedreiro (trabalho informal)"));
        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("renda"))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='ocupacao')].valor").value("Pedreiro (trabalho informal)"));
    }

    // ---- Ajuda por catálogo

    private ParticipanteApi naPerguntaDePessoasNaCasa() throws Exception {
        var participante = naOcupacao();
        interpretador.propor("ocupacao", "Diarista", "Diarista");
        participante.responderValor("Diarista").andExpect(status().isOk());
        participante.responderOpcao("SEM_RENDA")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("quantidadeMembros"));
        return participante;
    }

    @Test
    void duvidaEmPerguntaNumericaRecebeOVerbeteLocalERepeteAPergunta() throws Exception {
        var participante = naPerguntaDePessoasNaCasa();
        interpretador.ajuda("dependentes");
        var versao = participante.versao;
        var chamadasAntes = interpretador.pedidos.size();

        participante.responderValor("o que é dependente?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos[0]").value(startsWith("Ajuda: Dependentes são as pessoas da casa")))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("quantidadeMembros"))
                .andExpect(jsonPath("$.versao").value(versao))
                .andExpect(jsonPath("$.modoInteracao").value("ASSISTIDO_POR_IA"));

        var pedido = interpretador.pedidos.get(chamadasAntes);
        assertThat(pedido.campos()).isEmpty();
        assertThat(pedido.topicos()).extracting("id").contains("dependentes", "renda-mensal");
    }

    @Test
    void duvidaEmPerguntaDeTextoLivreTambemRecebeOVerbete() throws Exception {
        var participante = noMunicipio();
        interpretador.ajuda("finalidade-cadastro");

        participante.responderValor("para que serve isso?")
                .andExpect(jsonPath("$.avisos[0]").value(startsWith("Ajuda: Este cadastro reúne informações básicas")))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("municipio"));
    }

    @Test
    void topicoForaDoCatalogoNaoViraAjudaEOErroDoCampoVoltaSemTrocarDeModo() throws Exception {
        var participante = naPerguntaDePessoasNaCasa();
        interpretador.ajuda("topico-inventado");

        participante.responderValor("o que é dependente?")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("quantidadeMembros"));

        participante.navegador.get("/atual").andExpect(jsonPath("$.modoInteracao").value("ASSISTIDO_POR_IA"));
    }

    @Test
    void respostaDeUmaPalavraOuComDadoPessoalNaoConsultaAIaParaAjuda() throws Exception {
        var participante = naPerguntaDePessoasNaCasa();
        var chamadasAntes = interpretador.pedidos.size();

        participante.responderValor("muitas").andExpect(status().isBadRequest());
        participante.responderValor("somos 4 e meu CPF é 529.982.247-25").andExpect(status().isBadRequest());

        assertThat(interpretador.pedidos).hasSize(chamadasAntes);
    }

    @Test
    void falhaDoProvedorNaAjudaPassaAPerguntasDiretasMasRespostaForaDoContratoNao() throws Exception {
        var participante = naPerguntaDePessoasNaCasa();
        interpretador.falharCom(Categoria.RESPOSTA_INVALIDA);

        participante.responderValor("o que é dependente?").andExpect(status().isBadRequest());
        participante.navegador.get("/atual").andExpect(jsonPath("$.modoInteracao").value("ASSISTIDO_POR_IA"));

        interpretador.falharCom(Categoria.LIMITE_EXCEDIDO);
        participante.responderValor("o que é dependente?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoInteracao").value("ESTRUTURADO"))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("indisponível")));
    }

    @Test
    void ajudaPedidaNaPerguntaDeMunicipioNaoMudaORascunho() throws Exception {
        var participante = noMunicipio();
        interpretador.ajuda("moradia");
        var versao = participante.versao;

        participante.responderValor("o que é localidade?")
                .andExpect(jsonPath("$.avisos[0]").value(startsWith("Ajuda:")))
                .andExpect(jsonPath("$.versao").value(versao));
    }
}
