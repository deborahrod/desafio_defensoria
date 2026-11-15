package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.CookieSessao;
import io.github.deborahrod.defensoria.cadastro.ModoInteracao;
import io.github.deborahrod.defensoria.cadastro.ServicoSessao;
import io.github.deborahrod.defensoria.cadastro.SessaoCadastro;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cadastro")
class CadastroController {

    record RespostaRequest(
            @NotNull UUID mensagemId,
            @NotNull @PositiveOrZero Integer versao,
            @Size(max = 1000) String valor,
            @Size(max = 60) String opcao) {

        RespostaEntrada entrada() {
            return new RespostaEntrada(valor, opcao);
        }
    }

    record CorrecaoRequest(
            @NotNull @PositiveOrZero Integer versao,
            @Size(max = 1000) String valor,
            @Size(max = 60) String opcao) {

        RespostaEntrada entrada() {
            return new RespostaEntrada(valor, opcao);
        }
    }

    record ConfirmacaoRequest(@NotNull @PositiveOrZero Integer versaoRevisada, @NotNull UUID chaveIdempotencia) {
    }

    private final ServicoSessao sessoes;
    private final ServicoConversa conversa;
    private final ServicoResposta respostas;
    private final ServicoConfirmacao confirmacao;
    private final CookieSessao cookies;

    CadastroController(
            ServicoSessao sessoes,
            ServicoConversa conversa,
            ServicoResposta respostas,
            ServicoConfirmacao confirmacao,
            CookieSessao cookies) {
        this.sessoes = sessoes;
        this.conversa = conversa;
        this.respostas = respostas;
        this.confirmacao = confirmacao;
        this.cookies = cookies;
    }

    @PostMapping("/sessao")
    ResponseEntity<EstadoConversa> iniciar(
            @CookieValue(name = CookieSessao.NOME, required = false) String segredoAnterior) {
        if (segredoAnterior != null) {
            sessoes.encerrarPeloSegredo(segredoAnterior);
        }
        var iniciada = sessoes.iniciar(ModoInteracao.ESTRUTURADO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookies.criar(iniciada.segredo()).toString())
                .body(conversa.abertura(iniciada.sessao().getId()));
    }

    @GetMapping("/atual")
    EstadoConversa atual(SessaoCadastro sessao) {
        return conversa.estadoAtual(sessao.getId());
    }

    @PostMapping("/respostas")
    EstadoConversa responder(SessaoCadastro sessao, @Valid @RequestBody RespostaRequest corpo) {
        return respostas.responder(sessao.getId(), corpo.mensagemId(), corpo.versao(), corpo.entrada());
    }

    @PatchMapping("/campos/{campo}")
    EstadoConversa corrigir(
            SessaoCadastro sessao, @PathVariable String campo, @Valid @RequestBody CorrecaoRequest corpo) {
        return conversa.corrigir(sessao.getId(), campo, corpo.versao(), corpo.entrada());
    }

    @GetMapping("/revisao")
    Revisao revisao(SessaoCadastro sessao) {
        return conversa.revisao(sessao.getId());
    }

    @PostMapping("/confirmacao")
    EstadoConversa confirmar(SessaoCadastro sessao, @Valid @RequestBody ConfirmacaoRequest corpo) {
        confirmacao.confirmar(sessao.getId(), corpo.versaoRevisada(), corpo.chaveIdempotencia());
        // Lido depois do commit: o comprovante só aparece se a gravação foi concluída.
        return conversa.estadoAtual(sessao.getId());
    }

    @DeleteMapping("/sessao")
    ResponseEntity<Void> encerrar(SessaoCadastro sessao) {
        sessoes.encerrar(sessao.getId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                .build();
    }
}
