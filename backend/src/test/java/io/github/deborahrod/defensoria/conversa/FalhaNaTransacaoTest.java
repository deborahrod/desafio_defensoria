package io.github.deborahrod.defensoria.conversa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.deborahrod.defensoria.TesteIntegracao;
import io.github.deborahrod.defensoria.cadastro.ConfirmacaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.DeclaracaoSocioeconomicaRepository;
import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.PessoaCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.RepresentacaoDeclaradaRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Falha no último passo da gravação, com pessoa, declaração e representação já escritas na mesma
 * transação: nada pode ficar e nenhum comprovante pode aparecer.
 */
class FalhaNaTransacaoTest extends TesteIntegracao {

    @Autowired
    private PessoaCadastroRepository pessoas;

    @Autowired
    private DeclaracaoSocioeconomicaRepository declaracoes;

    @Autowired
    private RepresentacaoDeclaradaRepository representacoes;

    @Autowired
    private ConfirmacaoCadastroRepository confirmacoes;

    @Autowired
    private RascunhoCadastroRepository rascunhos;

    @Autowired
    private SessaoCadastroRepository sessoes;

    private ParticipanteApi emRevisao(List<RespostaEntrada> respostas) throws Exception {
        var participante = new ParticipanteApi(novoNavegador());
        participante.iniciar().andExpect(status().isCreated());
        participante.responderTudo(respostas);
        return participante;
    }

    @Test
    void falhaAoGravarAConfirmacaoDesfazTudoQueJaTinhaSidoEscritoNaTransacao() throws Exception {
        // A chave de idempotência é única no banco. Reutilizada por outra sessão, o último insert falha.
        var chave = UUID.randomUUID();
        var primeira = emRevisao(RespostasFicticias.fluxoComAusencias());
        primeira.confirmar(primeira.versao, chave).andExpect(status().isOk());
        var segunda = emRevisao(RespostasFicticias.fluxoParaOutraPessoa());
        var idDaSegunda = sessoes.findAll().stream()
                .filter(s -> s.getEstado() == EstadoSessao.EM_REVISAO)
                .findFirst().orElseThrow().getId();

        segunda.confirmar(segunda.versao, chave)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CADASTRO_CONFLITANTE"))
                .andExpect(jsonPath("$.mensagem").value(containsString("nada foi gravado")))
                .andExpect(jsonPath("$.comprovante").doesNotExist());

        // Pessoa, declaração e representação da segunda sessão chegaram a ser inseridas e foram desfeitas.
        assertThat(pessoas.count()).isEqualTo(1);
        assertThat(declaracoes.count()).isEqualTo(1);
        assertThat(confirmacoes.count()).isEqualTo(1);
        assertThat(representacoes.count()).isZero();
        assertThat(sessoes.findById(idDaSegunda)).get().extracting("estado").isEqualTo(EstadoSessao.EM_REVISAO);
        assertThat(rascunhos.findById(idDaSegunda)).isPresent();
        segunda.navegador.get("/atual")
                .andExpect(jsonPath("$.estado").value("EM_REVISAO"))
                .andExpect(jsonPath("$.comprovante").doesNotExist());

        // Com outra chave a mesma sessão conclui, e só então existe comprovante.
        segunda.confirmar(segunda.versao, UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprovante.identificador").exists());
        assertThat(pessoas.count()).isEqualTo(2);
        assertThat(representacoes.count()).isEqualTo(1);
    }
}
