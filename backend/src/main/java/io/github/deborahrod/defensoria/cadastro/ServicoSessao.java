package io.github.deborahrod.defensoria.cadastro;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServicoSessao {

    private final SessaoCadastroRepository sessoes;
    private final RascunhoCadastroRepository rascunhos;
    private final PropriedadesSessao propriedades;
    private final Clock relogio;

    public ServicoSessao(
            SessaoCadastroRepository sessoes,
            RascunhoCadastroRepository rascunhos,
            PropriedadesSessao propriedades,
            Clock relogio) {
        this.sessoes = sessoes;
        this.rascunhos = rascunhos;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    @Transactional
    public SessaoIniciada iniciar(ModoInteracao modo) {
        var segredo = SegredoSessao.gerar();
        var agora = relogio.instant();
        var sessao = sessoes.save(
                new SessaoCadastro(SegredoSessao.hash(segredo), modo, agora, propriedades.expiracao()));
        rascunhos.save(new RascunhoCadastro(sessao.getId(), agora));
        return new SessaoIniciada(sessao, segredo);
    }

    /** Localiza a sessão pelo segredo do cookie e renova o prazo de inatividade. */
    @Transactional(noRollbackFor = SessaoExpiradaException.class)
    public SessaoCadastro autenticar(String segredo) {
        var sessao = sessoes.findBySegredoHash(SegredoSessao.hash(segredo))
                .filter(encontrada -> encontrada.getEstado() != EstadoSessao.CANCELADO)
                .orElseThrow(SessaoInexistenteException::new);
        var agora = relogio.instant();
        if (sessao.getEstado() == EstadoSessao.EXPIRADO || sessao.expirouEm(agora)) {
            expirar(sessao);
            throw new SessaoExpiradaException();
        }
        var id = sessao.getId();
        sessoes.renovarPrazo(id, agora, agora.plus(propriedades.expiracao()));
        return sessoes.findById(id).orElseThrow(SessaoInexistenteException::new);
    }

    /** Encerra sem confirmar: o rascunho some, um cadastro já confirmado permanece. */
    @Transactional
    public void encerrar(UUID sessaoId) {
        sessoes.findById(sessaoId)
                .filter(sessao -> sessao.getEstado().emAndamento())
                .ifPresent(sessao -> {
                    sessao.cancelar();
                    rascunhos.deleteById(sessaoId);
                });
    }

    @Transactional
    public void encerrarPeloSegredo(String segredo) {
        sessoes.findBySegredoHash(SegredoSessao.hash(segredo))
                .ifPresent(sessao -> encerrar(sessao.getId()));
    }

    @Transactional
    public void limparExpiradas() {
        var agora = relogio.instant();
        sessoes.findByEstadoInAndExpiraEmLessThanEqual(EstadoSessao.EM_ANDAMENTO, agora)
                .forEach(this::expirar);
        sessoes.flush();
        sessoes.apagarEncerradasAte(
                List.of(EstadoSessao.EXPIRADO, EstadoSessao.CANCELADO),
                agora.minus(propriedades.retencaoEncerradas()));
    }

    private void expirar(SessaoCadastro sessao) {
        if (sessao.getEstado().emAndamento()) {
            sessao.expirar();
            rascunhos.deleteById(sessao.getId());
        }
    }
}
