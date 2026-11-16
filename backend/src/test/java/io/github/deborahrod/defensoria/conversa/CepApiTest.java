package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.cadastro.PessoaCadastroRepository;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CepApiTest extends TesteIntegracao {

    @Autowired
    private PessoaCadastroRepository pessoas;

    /** Responde tudo até a pergunta do CEP. O município digitado pode ser trocado para testar divergência. */
    private ParticipanteApi noCep(String municipio) throws Exception {
        var respostas = new ArrayList<>(RespostasFicticias.fluxoComum().subList(0, 12));
        respostas.set(9, RespostaEntrada.valor(municipio));
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        participante.responderTudo(respostas);
        participante.navegador.get("/atual").andExpect(jsonPath("$.proximaPergunta.campo").value("cep"));
        return participante;
    }

    private static String itemDoResumo(String campo) {
        return "$.resumo[*].itens[?(@.campo=='" + campo + "')].valor";
    }

    @Test
    void cepEncontradoMostraOEnderecoParaConferirEOPreencheDepoisDoSim() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Goiânia");

        participante.responderValor("74000-000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("CEP 74000-000")))
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("Rua das Flores Inventadas")))
                .andExpect(jsonPath("$.proximaPergunta.itens[*].rotulo").value(contains("Logradouro", "Bairro ou setor")))
                .andExpect(jsonPath("$.proximaPergunta.opcoes[*].rotulo").value(contains("Sim, está certo", "Não, vou preencher à mão")));
        assertThat(cep.consultas).containsExactly("74000000");

        participante.responderOpcao("CONFIRMAR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("numero"))
                .andExpect(jsonPath(itemDoResumo("cep")).value("74000-000"))
                .andExpect(jsonPath(itemDoResumo("logradouro")).value("Rua das Flores Inventadas"))
                .andExpect(jsonPath(itemDoResumo("bairro")).value("Setor Central"));
    }

    @Test
    void depoisDoSimSoFaltamNumeroEComplementoECadastroSeguePeloFluxoNormal() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Goiânia");
        participante.responderValor("74000-000");
        participante.responderOpcao("CONFIRMAR");

        participante.responderValor("123").andExpect(jsonPath("$.proximaPergunta.campo").value("complemento"));
        participante.responderOpcao("PULAR").andExpect(jsonPath("$.proximaPergunta.campo").value("ocupacao"));
        participante.responderTudo(RespostasFicticias.fluxoComum().subList(17, 22));
        participante.confirmar().andExpect(status().isOk());

        var endereco = pessoas.findAll().getFirst().getLocalizacao();
        assertThat(endereco.cep()).isEqualTo("74000000");
        assertThat(endereco.logradouro()).isEqualTo("Rua das Flores Inventadas");
        assertThat(endereco.numero()).isEqualTo("123");
        assertThat(endereco.bairro()).isEqualTo("Setor Central");
    }

    @Test
    void recusarOEnderecoDoCepSegueManualEMarcaOCepComoNaoUsado() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Goiânia");
        participante.responderValor("74000-000");

        participante.responderOpcao("MANTER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("logradouro"))
                .andExpect(jsonPath(itemDoResumo("cep")).value("Não preenchido"));
    }

    @Test
    void cepNaoEncontradoAvisaGuardaOCepDigitadoESeguePeloEnderecoManual() throws Exception {
        cep.naoEncontrar();
        var participante = noCep("Goiânia");

        participante.responderValor("74000-000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_PREENCHIMENTO"))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("Não encontrei esse CEP")))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("logradouro"))
                .andExpect(jsonPath(itemDoResumo("cep")).value("74000-000"));
    }

    @Test
    void provedoresForaDoArNaoBloqueiamOCadastro() throws Exception {
        cep.ficarForaDoAr();
        var participante = noCep("Goiânia");

        participante.responderValor("74000-000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisos[0]").value(containsString("Não consegui consultar o CEP")))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("logradouro"));
        participante.responderTudo(RespostasFicticias.fluxoComum().subList(13, 22));
        participante.confirmar().andExpect(status().isOk());
        assertThat(pessoas.findAll().getFirst().getLocalizacao().cep()).isEqualTo("74000000");
    }

    @Test
    void cepDeMunicipioSemRuaPedeLogradouroEBairroNaMao() throws Exception {
        cep.achar("", "", "Goiânia", "GO");
        var participante = noCep("Goiânia");

        participante.responderValor("74000-000")
                .andExpect(jsonPath("$.proximaPergunta.itens").value(hasSize(0)));
        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("logradouro"));
    }

    @Test
    void municipioIgualAoInformadoMesmoSemAcentoNaoGeraDivergencia() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("goiania");

        participante.responderValor("74000-000")
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("Está certo?")))
                .andExpect(jsonPath("$.proximaPergunta.itens[*].rotulo").value(contains("Logradouro", "Bairro ou setor")));
        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath(itemDoResumo("municipio")).value("goiania"));
    }

    @Test
    void municipioDiferenteDoInformadoPerguntaQualValeEUsarOCepTrocaMunicipioEUf() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Anápolis");

        participante.responderValor("74000-000")
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"))
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("é de Goiânia, GO")))
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("diferente do que você informou (Anápolis, GO)")))
                .andExpect(jsonPath("$.proximaPergunta.opcoes[*].rotulo").value(contains("Usar o endereço do CEP", "Manter o que informei")));

        participante.responderOpcao("CONFIRMAR")
                .andExpect(jsonPath(itemDoResumo("municipio")).value("Goiânia"))
                .andExpect(jsonPath(itemDoResumo("uf")).value("GO"))
                .andExpect(jsonPath(itemDoResumo("logradouro")).value("Rua das Flores Inventadas"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("numero"));
    }

    @Test
    void manterOQueFoiInformadoNaoMudaMunicipioNemPreencheEndereco() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Anápolis");
        participante.responderValor("74000-000");

        participante.responderOpcao("MANTER")
                .andExpect(jsonPath(itemDoResumo("municipio")).value("Anápolis"))
                .andExpect(jsonPath(itemDoResumo("cep")).value("Não preenchido"))
                .andExpect(jsonPath("$.proximaPergunta.campo").value("logradouro"));
    }

    @Test
    void ufDiferenteComMesmoMunicipioTambemPerguntaQualVale() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "MG");
        var participante = noCep("Goiânia");

        participante.responderValor("74000-000")
                .andExpect(jsonPath("$.proximaPergunta.texto").value(containsString("é de Goiânia, MG")))
                .andExpect(jsonPath("$.proximaPergunta.opcoes[*].rotulo").value(hasItem("Manter o que informei")));
    }

    @Test
    void cepComFormatoInvalidoNaoConsultaNada() throws Exception {
        var participante = noCep("Goiânia");

        participante.responderValor("740-00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cep"));

        assertThat(cep.consultas).isEmpty();
    }

    @Test
    void pularOCepNaoConsultaNadaEOEnderecoSegueManual() throws Exception {
        var participante = noCep("Goiânia");

        participante.responderOpcao("PULAR")
                .andExpect(jsonPath("$.proximaPergunta.campo").value("logradouro"));

        assertThat(cep.consultas).isEmpty();
    }

    @Test
    void reenvioDaMesmaMensagemNaoConsultaDeNovoNemDuplicaAConfirmacao() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Goiânia");
        var mensagem = UUID.randomUUID();
        var versao = participante.versao;

        participante.responder(mensagem, versao, "\"74000-000\"", "null").andExpect(status().isOk());
        participante.responder(mensagem, versao, "\"74000-000\"", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("AGUARDANDO_ESCLARECIMENTO"));

        assertThat(cep.consultas).hasSize(1);
    }

    @Test
    void corrigirOCepNoResumoNaoConsultaNemMudaOEndereco() throws Exception {
        cep.achar("Rua das Flores Inventadas", "Setor Central", "Goiânia", "GO");
        var participante = noCep("Goiânia");
        participante.responderValor("74000-000");
        participante.responderOpcao("CONFIRMAR");

        participante.corrigir("cep", "\"74000-111\"", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath(itemDoResumo("cep")).value("74000-111"))
                .andExpect(jsonPath(itemDoResumo("logradouro")).value("Rua das Flores Inventadas"));

        assertThat(cep.consultas).hasSize(1);
    }
}
