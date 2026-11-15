package io.github.deborahrod.defensoria.cadastro;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cadastro")
class SessaoController {

    private final ServicoSessao sessoes;
    private final CookieSessao cookies;
    private final PropriedadesSessao propriedades;

    SessaoController(ServicoSessao sessoes, CookieSessao cookies, PropriedadesSessao propriedades) {
        this.sessoes = sessoes;
        this.cookies = cookies;
        this.propriedades = propriedades;
    }

    @PostMapping("/sessao")
    ResponseEntity<SessaoResposta> iniciar(
            @CookieValue(name = CookieSessao.NOME, required = false) String segredoAnterior) {
        if (segredoAnterior != null) {
            sessoes.encerrarPeloSegredo(segredoAnterior);
        }
        var iniciada = sessoes.iniciar(ModoInteracao.ESTRUTURADO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookies.criar(iniciada.segredo()).toString())
                .body(SessaoResposta.de(iniciada.sessao(), propriedades));
    }

    @GetMapping("/atual")
    SessaoResposta atual(SessaoCadastro sessao) {
        return SessaoResposta.de(sessao, propriedades);
    }

    @DeleteMapping("/sessao")
    ResponseEntity<Void> encerrar(SessaoCadastro sessao) {
        sessoes.encerrar(sessao.getId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                .build();
    }
}
