package io.github.deborahrod.defensoria.cadastro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.deborahrod.defensoria.TesteIntegracao;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class PersistenciaCadastroTest extends TesteIntegracao {

    private static final Instant AGORA = Instant.parse("2026-11-15T12:00:00Z");
    private static final LocalDate COMPETENCIA = LocalDate.of(2026, 11, 1);

    @Autowired
    private SessaoCadastroRepository sessoes;

    @Autowired
    private RascunhoCadastroRepository rascunhos;

    @Autowired
    private PessoaCadastroRepository pessoas;

    @Autowired
    private DeclaracaoSocioeconomicaRepository declaracoes;

    @Autowired
    private ConfirmacaoCadastroRepository confirmacoes;

    @Test
    void variasPessoasPodemFicarSemCpf() {
        var primeira = pessoas.saveAndFlush(pessoaSemCpf("Ana Fictícia", SituacaoCpf.NAO_POSSUI));
        var segunda = pessoas.saveAndFlush(pessoaSemCpf("Bia Fictícia", SituacaoCpf.DESCONHECIDO));

        assertThat(pessoas.findById(primeira.getId())).get().extracting(PessoaCadastro::getCpf).isNull();
        assertThat(pessoas.findById(segunda.getId())).get().extracting(PessoaCadastro::getCpf).isNull();
    }

    @Test
    void cpfPresenteNaoPodeRepetir() {
        pessoas.saveAndFlush(pessoaComCpf("Caio Fictício", "52998224725"));

        assertThatThrownBy(() -> pessoas.saveAndFlush(pessoaComCpf("Outro Fictício", "52998224725")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void situacaoInformadoExigeCpfEAusenciaExigeCpfNulo() {
        var informadoSemCpf = pessoa("Dora Fictícia", SituacaoCpf.INFORMADO, null);
        var ausenteComCpf = pessoa("Enzo Fictício", SituacaoCpf.NAO_APRESENTADO, "11144477735");

        assertThatThrownBy(() -> pessoas.saveAndFlush(informadoSemCpf))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> pessoas.saveAndFlush(ausenteComCpf))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rendaGuardaCentavosSemArredondar() {
        var sessao = novaSessao();
        var pessoa = pessoas.saveAndFlush(pessoaSemCpf("Fabi Fictícia", SituacaoCpf.NAO_POSSUI));

        var salva = declaracoes.saveAndFlush(declaracao(pessoa, sessao, new BigDecimal("1518.35"), 3, 2));

        var lida = declaracoes.findById(salva.getId()).orElseThrow();
        assertThat(lida.getRendaDeclarada()).isEqualByComparingTo("1518.35");
        assertThat(lida.getRendaDeclarada().scale()).isEqualTo(2);
        assertThat(lida.getCompetencia()).isEqualTo(COMPETENCIA);
    }

    @Test
    void rendaZeroEValorEAusenciaFicaNula() {
        var sessao = novaSessao();
        var pessoa = pessoas.saveAndFlush(pessoaSemCpf("Gil Fictício", SituacaoCpf.NAO_POSSUI));

        var semRenda = declaracoes.saveAndFlush(declaracao(pessoa, sessao, BigDecimal.ZERO, 1, 0));
        var desconhecida = declaracoes.saveAndFlush(new DeclaracaoSocioeconomica(
                pessoa.getId(), sessao.getId(), COMPETENCIA, null, null, null, null, null,
                Map.of(Campo.RENDA.id(), MotivoAusencia.DESCONHECIDO.name()), AGORA));

        assertThat(declaracoes.findById(semRenda.getId()).orElseThrow().getRendaDeclarada()).isEqualByComparingTo("0");
        var lida = declaracoes.findById(desconhecida.getId()).orElseThrow();
        assertThat(lida.getRendaDeclarada()).isNull();
        assertThat(lida.getAusencias()).containsEntry("renda", "DESCONHECIDO");
    }

    @Test
    void bancoRecusaRendaNegativaENucleoIncoerente() {
        var sessao = novaSessao();
        var pessoa = pessoas.saveAndFlush(pessoaSemCpf("Hugo Fictício", SituacaoCpf.NAO_POSSUI));

        assertThatThrownBy(() -> declaracoes.saveAndFlush(declaracao(pessoa, sessao, new BigDecimal("-1.00"), 2, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> declaracoes.saveAndFlush(declaracao(pessoa, sessao, BigDecimal.TEN, 0, 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> declaracoes.saveAndFlush(declaracao(pessoa, sessao, BigDecimal.TEN, 2, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rascunhoGuardaValoresAusenciasEAvancaAVersao() {
        var sessao = novaSessao();
        var rascunho = new RascunhoCadastro(sessao.getId(), AGORA);
        rascunho.informar(Campo.NOME, "Íris Fictícia", AGORA);
        rascunho.registrarAusencia(Campo.NASCIMENTO, MotivoAusencia.DESCONHECIDO, AGORA);
        rascunhos.saveAndFlush(rascunho);

        var lido = rascunhos.findById(sessao.getId()).orElseThrow();
        assertThat(lido.getVersao()).isEqualTo(2);
        assertThat(lido.valor(Campo.NOME)).contains("Íris Fictícia");
        assertThat(lido.ausencia(Campo.NASCIMENTO)).contains(MotivoAusencia.DESCONHECIDO);
        assertThat(lido.respondido(Campo.CPF)).isFalse();

        lido.informar(Campo.NASCIMENTO, "1990-05-20", AGORA);
        rascunhos.saveAndFlush(lido);

        var corrigido = rascunhos.findById(sessao.getId()).orElseThrow();
        assertThat(corrigido.getVersao()).isEqualTo(3);
        assertThat(corrigido.valor(Campo.NASCIMENTO)).contains("1990-05-20");
        assertThat(corrigido.ausencia(Campo.NASCIMENTO)).isEmpty();
    }

    @Test
    void sessaoAceitaUmaUnicaConfirmacao() {
        var sessao = novaSessao();
        var pessoa = pessoas.saveAndFlush(pessoaSemCpf("Joana Fictícia", SituacaoCpf.NAO_POSSUI));
        confirmacoes.saveAndFlush(new ConfirmacaoCadastro(
                sessao.getId(), 4, UUID.randomUUID(), pessoa.getId(), false, AGORA));

        var repetida = new ConfirmacaoCadastro(sessao.getId(), 4, UUID.randomUUID(), pessoa.getId(), false, AGORA);

        assertThatThrownBy(() -> confirmacoes.saveAndFlush(repetida))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void apagarSessaoLevaORascunhoJunto() {
        var sessao = novaSessao();
        rascunhos.saveAndFlush(new RascunhoCadastro(sessao.getId(), AGORA));

        sessoes.deleteById(sessao.getId());
        sessoes.flush();

        assertThat(rascunhos.findById(sessao.getId())).isEmpty();
    }

    private SessaoCadastro novaSessao() {
        return sessoes.saveAndFlush(new SessaoCadastro(
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                ModoInteracao.ESTRUTURADO,
                AGORA,
                Duration.ofMinutes(30)));
    }

    private DeclaracaoSocioeconomica declaracao(
            PessoaCadastro pessoa, SessaoCadastro sessao, BigDecimal renda, int membros, int dependentes) {
        return new DeclaracaoSocioeconomica(
                pessoa.getId(), sessao.getId(), COMPETENCIA, "Diarista", renda, membros, dependentes,
                SituacaoMoradia.ALUGADA, Map.of(), AGORA);
    }

    private PessoaCadastro pessoaSemCpf(String nome, SituacaoCpf situacao) {
        return pessoa(nome, situacao, null);
    }

    private PessoaCadastro pessoaComCpf(String nome, String cpf) {
        return pessoa(nome, SituacaoCpf.INFORMADO, cpf);
    }

    private PessoaCadastro pessoa(String nome, SituacaoCpf situacao, String cpf) {
        return new PessoaCadastro(
                nome, null, null, situacao, cpf, Contato.indisponivel(), Localizacao.naoInformada(), Map.of(), AGORA);
    }
}
