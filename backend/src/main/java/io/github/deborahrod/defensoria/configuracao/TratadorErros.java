package io.github.deborahrod.defensoria.configuracao;

import io.github.deborahrod.defensoria.cadastro.CookieSessao;
import io.github.deborahrod.defensoria.cadastro.SessaoExpiradaException;
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class TratadorErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorErros.class);

    private final CookieSessao cookies;

    TratadorErros(CookieSessao cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(SessaoInexistenteException.class)
    ResponseEntity<ErroResposta> semSessao() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErroResposta(
                "SESSAO_INEXISTENTE", "Não há cadastro em andamento. Inicie um novo cadastro."));
    }

    @ExceptionHandler(SessaoExpiradaException.class)
    ResponseEntity<ErroResposta> sessaoExpirada() {
        return ResponseEntity.status(HttpStatus.GONE)
                .header(HttpHeaders.SET_COOKIE, cookies.apagar().toString())
                .body(new ErroResposta(
                        "SESSAO_EXPIRADA",
                        "Sua sessão expirou por inatividade e os dados não confirmados foram apagados. "
                                + "Inicie um novo cadastro."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResposta> falhaInesperada(Exception erro) {
        // Só o tipo da exceção: a mensagem pode carregar dados da requisição.
        LOG.error("Falha inesperada: {}", erro.getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErroResposta(
                "ERRO_INTERNO", "Não foi possível concluir a operação. Tente novamente em instantes."));
    }

    // Erros do próprio Spring MVC (rota, método, corpo ilegível) saem no mesmo formato dos demais.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception erro, Object corpo, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        var resposta = status.is4xxClientError()
                ? new ErroResposta("REQUISICAO_INVALIDA", "A requisição não pôde ser atendida da forma enviada.")
                : new ErroResposta("ERRO_INTERNO", "Não foi possível concluir a operação. Tente novamente em instantes.");
        return ResponseEntity.status(status).headers(cabecalhos).body(resposta);
    }
}
