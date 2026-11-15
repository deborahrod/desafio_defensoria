package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.cadastro.Campo.*;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.ConfirmacaoCadastro;
import io.github.deborahrod.defensoria.cadastro.ConfirmacaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.Contato;
import io.github.deborahrod.defensoria.cadastro.DeclaracaoSocioeconomica;
import io.github.deborahrod.defensoria.cadastro.DeclaracaoSocioeconomicaRepository;
import io.github.deborahrod.defensoria.cadastro.EstadoSessao;
import io.github.deborahrod.defensoria.cadastro.FormaContato;
import io.github.deborahrod.defensoria.cadastro.Localizacao;
import io.github.deborahrod.defensoria.cadastro.PessoaCadastro;
import io.github.deborahrod.defensoria.cadastro.PessoaCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.RepresentacaoDeclarada;
import io.github.deborahrod.defensoria.cadastro.RepresentacaoDeclaradaRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastroRepository;
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import io.github.deborahrod.defensoria.cadastro.SituacaoCpf;
import io.github.deborahrod.defensoria.cadastro.SituacaoMoradia;
import io.github.deborahrod.defensoria.integracao.ServicoEventos;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Grava o cadastro de uma vez: pessoa, declaração e confirmação na mesma transação.
 * Quem chama só recebe o comprovante depois do commit.
 */
@Service
public class ServicoConfirmacao {

    private static final List<Campo> CAMPOS_SOCIOECONOMICOS =
            List.of(OCUPACAO, RENDA, QUANTIDADE_MEMBROS, DEPENDENTES, SITUACAO_MORADIA);

    private final SessaoCadastroRepository sessoes;
    private final RascunhoCadastroRepository rascunhos;
    private final PessoaCadastroRepository pessoas;
    private final RepresentacaoDeclaradaRepository representacoes;
    private final DeclaracaoSocioeconomicaRepository declaracoes;
    private final ConfirmacaoCadastroRepository confirmacoes;
    private final RoteiroCadastro roteiro;
    private final MontadorEstado montador;
    private final ServicoEventos eventos;
    private final Clock relogio;

    public ServicoConfirmacao(
            SessaoCadastroRepository sessoes,
            RascunhoCadastroRepository rascunhos,
            PessoaCadastroRepository pessoas,
            RepresentacaoDeclaradaRepository representacoes,
            DeclaracaoSocioeconomicaRepository declaracoes,
            ConfirmacaoCadastroRepository confirmacoes,
            RoteiroCadastro roteiro,
            MontadorEstado montador,
            ServicoEventos eventos,
            Clock relogio) {
        this.sessoes = sessoes;
        this.rascunhos = rascunhos;
        this.pessoas = pessoas;
        this.representacoes = representacoes;
        this.declaracoes = declaracoes;
        this.confirmacoes = confirmacoes;
        this.roteiro = roteiro;
        this.montador = montador;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Transactional
    public Comprovante confirmar(UUID sessaoId, int versaoRevisada, UUID chaveIdempotencia) {
        var sessao = sessoes.buscarComTrava(sessaoId).orElseThrow(SessaoInexistenteException::new);
        // Repetição da confirmação (duplo clique, resposta perdida): devolve o resultado original.
        var anterior = confirmacoes.findBySessaoId(sessaoId);
        if (anterior.isPresent()) {
            return Comprovante.de(anterior.get());
        }
        if (sessao.getEstado() != EstadoSessao.EM_REVISAO) {
            throw new EstadoInvalidoException("O cadastro só pode ser confirmado depois da revisão.");
        }
        var rascunho = rascunhos.findById(sessaoId)
                .orElseThrow(() -> new EstadoInvalidoException("Este cadastro não está mais em preenchimento."));
        if (rascunho.getVersao() != versaoRevisada) {
            throw new VersaoDesatualizadaException();
        }

        var agora = relogio.instant();
        var pessoa = gravarPessoa(rascunho, agora);
        gravarDeclaracao(rascunho, pessoa, sessaoId, agora);
        if (RoteiroCadastro.paraOutraPessoa(rascunho)) {
            representacoes.save(new RepresentacaoDeclarada(
                    pessoa.getId(),
                    rascunho.valor(NOME_QUEM_PREENCHE).orElseThrow(),
                    rascunho.valor(VINCULO_REPRESENTACAO).orElseThrow(),
                    agora));
        }
        var comPendencias = !montador.pendencias(rascunho).isEmpty();
        ConfirmacaoCadastro confirmacao;
        try {
            confirmacao = confirmacoes.saveAndFlush(new ConfirmacaoCadastro(
                    sessaoId, versaoRevisada, chaveIdempotencia, pessoa.getId(), comPendencias, agora));
        } catch (DataIntegrityViolationException e) {
            // Por exemplo, chave de idempotência já usada por outra sessão: nada do que foi gravado fica.
            throw conflito();
        }

        sessao.mudarPara(EstadoSessao.CONFIRMADO);
        rascunhos.delete(rascunho);
        // O evento só é registrado depois do commit: falha na transação não deixa rastro de sucesso.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventos.registrar("CONFIRMACAO", "SUCESSO", null, null, null);
            }
        });
        return Comprovante.de(confirmacao);
    }

    private CadastroConflitanteException conflito() {
        eventos.registrar("CONFIRMACAO", "CONFLITO_CADASTRAL", null, null, null);
        return new CadastroConflitanteException();
    }

    private PessoaCadastro gravarPessoa(RascunhoCadastro rascunho, Instant agora) {
        var cpf = rascunho.valor(CPF).orElse(null);
        // A checagem prévia evita o erro comum; o índice único cobre a corrida entre duas sessões.
        if (cpf != null && pessoas.existsByCpf(cpf)) {
            throw conflito();
        }
        var campos = roteiro.aplicaveis(rascunho).map(pergunta -> pergunta.campo())
                .filter(campo -> !CAMPOS_SOCIOECONOMICOS.contains(campo))
                .toList();
        var pessoa = new PessoaCadastro(
                rascunho.valor(NOME).orElseThrow(),
                rascunho.valor(NOME_SOCIAL).orElse(null),
                rascunho.valor(NASCIMENTO).map(LocalDate::parse).orElse(null),
                SituacaoCpf.valueOf(rascunho.valor(SITUACAO_CPF).orElseThrow()),
                cpf,
                contato(rascunho),
                localizacao(rascunho),
                rascunho.ausenciasDe(campos),
                agora);
        try {
            return pessoas.saveAndFlush(pessoa);
        } catch (DataIntegrityViolationException e) {
            throw conflito();
        }
    }

    private void gravarDeclaracao(RascunhoCadastro rascunho, PessoaCadastro pessoa, UUID sessaoId, Instant agora) {
        var competencia = LocalDate.ofInstant(agora, ServicoConversa.FUSO).withDayOfMonth(1);
        declaracoes.save(new DeclaracaoSocioeconomica(
                pessoa.getId(),
                sessaoId,
                competencia,
                rascunho.valor(OCUPACAO).orElse(null),
                rascunho.valor(RENDA).map(BigDecimal::new).orElse(null),
                rascunho.valor(QUANTIDADE_MEMBROS).map(Integer::valueOf).orElse(null),
                rascunho.valor(DEPENDENTES).map(Integer::valueOf).orElse(null),
                rascunho.valor(SITUACAO_MORADIA).map(SituacaoMoradia::valueOf).orElse(null),
                rascunho.ausenciasDe(CAMPOS_SOCIOECONOMICOS),
                agora));
    }

    private static Contato contato(RascunhoCadastro rascunho) {
        return new Contato(
                FormaContato.valueOf(rascunho.valor(FORMA_CONTATO).orElseThrow()),
                rascunho.valor(TELEFONE).orElse(null),
                rascunho.valor(EMAIL).orElse(null),
                rascunho.valor(REFERENCIA_NOME).orElse(null),
                rascunho.valor(REFERENCIA_VINCULO).orElse(null),
                rascunho.valor(REFERENCIA_CANAL).orElse(null));
    }

    private static Localizacao localizacao(RascunhoCadastro rascunho) {
        return new Localizacao(
                rascunho.valor(MUNICIPIO).orElse(null),
                rascunho.valor(UF).orElse(null),
                rascunho.tem(ENDERECO_FIXO, RoteiroCadastro.SIM),
                rascunho.valor(LOGRADOURO).orElse(null),
                rascunho.valor(NUMERO).orElse(null),
                rascunho.valor(COMPLEMENTO).orElse(null),
                rascunho.valor(BAIRRO).orElse(null),
                rascunho.valor(CEP).orElse(null),
                rascunho.valor(REFERENCIA_LOCALIZACAO).orElse(null));
    }
}
