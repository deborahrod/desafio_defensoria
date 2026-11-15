package io.github.deborahrod.defensoria.configuracao;

import io.github.deborahrod.defensoria.cadastro.CookieSessao;
import io.github.deborahrod.defensoria.cadastro.SessaoExpiradaException;
import io.github.deborahrod.defensoria.cadastro.SessaoInexistenteException;
import io.github.deborahrod.defensoria.conversa.CadastroConflitanteException;
import io.github.deborahrod.defensoria.conversa.EstadoInvalidoException;
import io.github.deborahrod.defensoria.conversa.LimiteExcedidoException;
import io.github.deborahrod.defensoria.conversa.RespostaInvalidaException;
import io.github.deborahrod.defensoria.conversa.VersaoDesatualizadaException;
import org.slf4j.Logger;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(RespostaInvalidaException.class)
    ResponseEntity<ErroResposta> respostaInvalida(RespostaInvalidaException erro) {
        return ResponseEntity.badRequest()
                .body(new ErroResposta("RESPOSTA_INVALIDA", erro.getMessage(), erro.getCampo()));
    }

    @ExceptionHandler({VersaoDesatualizadaException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ErroResposta> versaoDesatualizada() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResposta(
                "VERSAO_DESATUALIZADA", "O cadastro mudou desde a última tela. Atualize para ver o estado atual."));
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    ResponseEntity<ErroResposta> limiteExcedido(LimiteExcedidoException erro) {
        var resposta = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (erro.getEsperaEmSegundos() != null) {
            resposta.header(HttpHeaders.RETRY_AFTER, String.valueOf(erro.getEsperaEmSegundos()));
        }
        return resposta.body(new ErroResposta("LIMITE_EXCEDIDO", erro.getMessage()));
    }

    @ExceptionHandler(CadastroConflitanteException.class)
    ResponseEntity<ErroResposta> cadastroConflitante() {
        // Resposta genérica de propósito: não revela se já existe cadastro com esses dados.
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResposta(
                "CADASTRO_CONFLITANTE",
                "Não foi possível concluir a gravação deste cadastro e nada foi gravado. "
                        + "Procure o atendimento da Defensoria para continuar com ajuda de uma pessoa."));
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    ResponseEntity<ErroResposta> estadoInvalido(EstadoInvalidoException erro) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResposta("ESTADO_INVALIDO", erro.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        var campo = erro.getBindingResult().getFieldErrors().stream().map(e -> e.getField()).findFirst().orElse(null);
        return ResponseEntity.badRequest().body(new ErroResposta(
                "REQUISICAO_INVALIDA", "Confira os dados enviados e tente novamente.", campo));
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
