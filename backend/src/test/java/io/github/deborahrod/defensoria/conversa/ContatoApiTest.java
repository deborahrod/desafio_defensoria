package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.cadastro.PessoaCadastroRepository;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContatoApiTest extends TesteIntegracao {

    @Autowired
    private PessoaCadastroRepository pessoas;

    private ParticipanteApi noTelefone() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        participante.responderTudo(RespostasFicticias.fluxoComum().subList(0, 7));
        participante.navegador.get("/atual").andExpect(jsonPath("$.proximaPergunta.campo").value("telefone"));
        return participante;
    }

    @Test
    void telefoneInvalidoVoltaComAMensagemQueDizOQueCorrigir() throws Exception {
        var participante = noTelefone();

        participante.responderValor("(02) 91234-5678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("telefone"))
                .andExpect(jsonPath("$.mensagem").value(containsString("O DDD 02 não existe")));
        participante.responderValor("(62) 81234-5678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("Celular tem 11 números")));
        participante.responderValor("(62) 99999-9999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("todos os dígitos são iguais")));

        participante.navegador.get("/atual").andExpect(jsonPath("$.proximaPergunta.campo").value("telefone"));
        participante.responderValor("(62) 91234-5678").andExpect(status().isOk());
    }

    @Test
    void cpfComNumerosIguaisOuDigitoErradoEhRecusadoNoBackendMesmoSemAInterface() throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar();
        participante.responderTudo(RespostasFicticias.fluxoComum().subList(0, 5));

        participante.responderValor("111.111.111-11")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("cpf"))
                .andExpect(jsonPath("$.mensagem").value(containsString("números são iguais")));
        participante.responderValor("529.982.247-26")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("dígitos verificadores não conferem")));
        participante.responderValor("529.982.247")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("11 números")));
    }

    @Test
    void naoTenhoTelefoneRegistraAAusenciaApareceComoPendenciaEGravaTelefoneNulo() throws Exception {
        var respostas = new ArrayList<>(RespostasFicticias.fluxoComum());
        respostas.set(7, RespostaEntrada.opcao("SEM_TELEFONE"));
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar();
        participante.responderTudo(respostas);

        participante.navegador.get("/atual")
                .andExpect(jsonPath("$.estado").value("EM_REVISAO"))
                .andExpect(jsonPath("$.pendencias[*]").value(org.hamcrest.Matchers.hasItem("Telefone: Não possui")))
                .andExpect(jsonPath("$.resumo[*].itens[?(@.campo=='telefone')].valor").value("Não possui"));

        participante.confirmar().andExpect(status().isOk()).andExpect(jsonPath("$.comprovante.comPendencias").value(true));
        var pessoa = pessoas.findAll().getFirst();
        assertThat(pessoa.getContato().telefone()).isNull();
        assertThat(pessoa.getAusencias()).containsEntry("telefone", "NAO_POSSUI");
    }
}
