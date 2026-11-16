package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Uma etapa da conversa: o que perguntar, como receber a resposta e quando ela se aplica. */
public final class Pergunta {

    @FunctionalInterface
    public interface Validador {
        Validacao validar(String bruto, ContextoResposta contexto);
    }

    /** Botão oferecido junto da pergunta. Registra um valor ou o motivo da ausência. */
    public record Alternativa(String id, String rotulo, String valor, MotivoAusencia ausencia) {

        public static Alternativa valor(String id, String rotulo) {
            return new Alternativa(id, rotulo, id, null);
        }

        public static Alternativa valorFixo(String id, String rotulo, String valor) {
            return new Alternativa(id, rotulo, valor, null);
        }

        public static Alternativa ausencia(String id, String rotulo, MotivoAusencia motivo) {
            return new Alternativa(id, rotulo, null, motivo);
        }
    }

    private final Campo campo;
    private final Secao secao;
    private final String rotulo;
    private final TipoEntrada tipoEntrada;
    private String textoProprio;
    private String textoTerceiro;
    private String ajuda;
    private Integer tamanhoMaximo;
    private boolean opcional;
    private String descricaoIa;
    private boolean textoInterpretavel;
    private boolean noRelato;
    private Campo sugeridaCom;
    private boolean noResumo = true;
    private Validador validador;
    private Predicate<RascunhoCadastro> aplicavel = rascunho -> true;
    private Function<RascunhoCadastro, List<Alternativa>> alternativas = rascunho -> List.of();

    private Pergunta(Campo campo, Secao secao, String rotulo, TipoEntrada tipoEntrada) {
        this.campo = campo;
        this.secao = secao;
        this.rotulo = rotulo;
        this.tipoEntrada = tipoEntrada;
    }

    static Pergunta de(Campo campo, Secao secao, String rotulo, TipoEntrada tipoEntrada) {
        return new Pergunta(campo, secao, rotulo, tipoEntrada);
    }

    Pergunta perguntar(String proprio, String terceiro) {
        this.textoProprio = proprio;
        this.textoTerceiro = terceiro;
        return this;
    }

    Pergunta perguntar(String texto) {
        return perguntar(texto, texto);
    }

    Pergunta ajuda(String ajuda) {
        this.ajuda = ajuda;
        return this;
    }

    Pergunta validar(Validador validador, int tamanhoMaximo) {
        this.validador = validador;
        this.tamanhoMaximo = tamanhoMaximo;
        return this;
    }

    /** Texto livre que a assistência por IA pode interpretar, com a descrição enviada ao modelo. */
    Pergunta interpretavelPorIa(String descricao) {
        this.descricaoIa = descricao;
        this.textoInterpretavel = true;
        return this;
    }

    /** O campo pode ser preenchido pelo relato livre, com a descrição enviada ao modelo. */
    Pergunta noRelato(String descricao) {
        this.descricaoIa = descricao;
        this.noRelato = true;
        return this;
    }

    /** Campo que o modelo também pode sugerir quando a pergunta atual é a do campo indicado. */
    Pergunta sugeridaCom(Campo campo) {
        this.sugeridaCom = campo;
        return this;
    }

    /** Pergunta só da conversa: não é dado do cadastro e não aparece na revisão. */
    Pergunta foraDoResumo() {
        this.noResumo = false;
        return this;
    }

    Pergunta comoOpcional() {
        this.opcional = true;
        return this;
    }

    Pergunta quando(Predicate<RascunhoCadastro> condicao) {
        this.aplicavel = condicao;
        return this;
    }

    Pergunta alternativas(Alternativa... fixas) {
        var lista = List.of(fixas);
        this.alternativas = rascunho -> lista;
        return this;
    }

    Pergunta alternativas(Function<RascunhoCadastro, List<Alternativa>> dinamicas) {
        this.alternativas = dinamicas;
        return this;
    }

    public Resolucao resolver(RespostaEntrada entrada, ContextoResposta contexto) {
        if (entrada.opcao() != null) {
            return alternativas.apply(contexto.rascunho()).stream()
                    .filter(alternativa -> alternativa.id().equals(entrada.opcao()))
                    .findFirst()
                    .<Resolucao>map(alternativa -> alternativa.ausencia() != null
                            ? new Resolucao.Ausente(alternativa.ausencia())
                            : new Resolucao.Valor(alternativa.valor()))
                    .orElseGet(() -> new Resolucao.Invalida("Escolha uma das opções apresentadas."));
        }
        if (validador == null) {
            return new Resolucao.Invalida("Escolha uma das opções apresentadas.");
        }
        if (entrada.valor() == null) {
            return new Resolucao.Invalida("Informe uma resposta para continuar.");
        }
        return switch (validador.validar(entrada.valor(), contexto)) {
            case Validacao.Aceita aceita -> new Resolucao.Valor(aceita.valor());
            case Validacao.Recusada recusada -> new Resolucao.Invalida(recusada.mensagem());
        };
    }

    /** Um valor já guardado pode deixar de valer quando outra resposta muda. */
    public boolean aindaAceita(String valor, ContextoResposta contexto) {
        if (alternativas.apply(contexto.rascunho()).stream().anyMatch(a -> valor.equals(a.valor()))) {
            return true;
        }
        return validador != null && validador.validar(valor, contexto) instanceof Validacao.Aceita;
    }

    public boolean aceitaAusencia(MotivoAusencia motivo, RascunhoCadastro rascunho) {
        return alternativas.apply(rascunho).stream().anyMatch(alternativa -> alternativa.ausencia() == motivo);
    }

    public String exibir(String valor, RascunhoCadastro rascunho) {
        return alternativas.apply(rascunho).stream()
                .filter(alternativa -> valor.equals(alternativa.valor()))
                .findFirst()
                .map(alternativa -> validador == null ? alternativa.rotulo() : complementar(valor, alternativa))
                .orElseGet(() -> Formatador.exibir(tipoEntrada, valor));
    }

    public boolean aplicavel(RascunhoCadastro rascunho) {
        return aplicavel.test(rascunho);
    }

    public String texto(boolean paraOutraPessoa) {
        return paraOutraPessoa ? textoTerceiro : textoProprio;
    }

    public List<Opcao> opcoes(RascunhoCadastro rascunho) {
        return alternativas.apply(rascunho).stream()
                .map(alternativa -> new Opcao(alternativa.id(), alternativa.rotulo()))
                .toList();
    }

    public Campo campo() {
        return campo;
    }

    public Secao secao() {
        return secao;
    }

    public String rotulo() {
        return rotulo;
    }

    public TipoEntrada tipoEntrada() {
        return tipoEntrada;
    }

    public String ajuda() {
        return ajuda;
    }

    public Integer tamanhoMaximo() {
        return tamanhoMaximo;
    }

    public boolean interpretavelPorIa() {
        return textoInterpretavel;
    }

    public boolean participaDoRelato() {
        return noRelato;
    }

    public Optional<Campo> sugeridaCom() {
        return Optional.ofNullable(sugeridaCom);
    }

    /** Pergunta só de botões ou lista, sem texto livre: a IA só pode escolher um dos ids oferecidos. */
    public boolean escolhaFechada() {
        return validador == null;
    }

    public List<String> idsDeOpcoes(RascunhoCadastro rascunho) {
        return alternativas.apply(rascunho).stream()
                .filter(alternativa -> alternativa.valor() != null)
                .map(Alternativa::id)
                .toList();
    }

    public String descricaoIa() {
        return descricaoIa;
    }

    public boolean noResumo() {
        return noResumo;
    }

    public boolean opcional() {
        return opcional;
    }

    // Valor fixo de um botão em campo digitável, como "Não tenho renda" para R$ 0,00.
    private String complementar(String valor, Alternativa alternativa) {
        return Formatador.exibir(tipoEntrada, valor) + " (" + alternativa.rotulo().toLowerCase() + ")";
    }
}
