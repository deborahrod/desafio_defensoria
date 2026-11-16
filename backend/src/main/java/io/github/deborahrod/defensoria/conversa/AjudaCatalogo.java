package io.github.deborahrod.defensoria.conversa;

import io.github.deborahrod.defensoria.integracao.PedidoInterpretacao.Topico;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Verbetes de ajuda em linguagem simples. O modelo só aponta o id: o texto exibido é sempre este,
 * escrito aqui. Nenhum verbete dá parecer jurídico nem diz quem será atendido.
 */
@Component
public class AjudaCatalogo {

    private record Verbete(String descricao, String texto) {
    }

    private final Map<String, Verbete> verbetes = new LinkedHashMap<>();

    public AjudaCatalogo() {
        adicionar("nome-social", "o que é nome social",
                "Nome social é o nome pelo qual a pessoa se identifica e prefere ser chamada. "
                        + "É opcional: você pode pular.");
        adicionar("por-que-cpf", "por que pedimos o CPF e o que fazer sem o documento",
                "O CPF ajuda a identificar o cadastro. Se você não tiver o número agora, tudo bem: "
                        + "escolha a opção que se aplica e a ausência fica registrada para o atendimento.");
        adicionar("documento-ausente", "o que acontece se faltar algum documento",
                "Se algum documento não estiver com você, escolha a opção que se aplica. A falta fica registrada "
                        + "para ser resolvida no atendimento e não impede este cadastro.");
        adicionar("ocupacao", "o que é ocupação",
                "Ocupação é o que você faz hoje para se sustentar. Por exemplo: autônomo, diarista, "
                        + "desempregada, aposentada ou do lar.");
        adicionar("renda-mensal", "o que contar como renda mensal",
                "Informe quanto entra por mês para você: salário, benefício, pensão ou bicos. Se não tem renda, "
                        + "escolha \"Não tenho renda\". É só o que você declara e não decide o atendimento.");
        adicionar("nucleo-familiar", "o que é núcleo familiar ou quem conta como morador da casa",
                "Núcleo familiar é o grupo que mora na mesma casa e divide as despesas, contando com você.");
        adicionar("dependentes", "o que são dependentes",
                "Dependentes são as pessoas da casa que precisam do seu sustento, como filhos, pais idosos ou "
                        + "outros familiares. Conte só quem depende de você.");
        adicionar("moradia", "como responder sobre a moradia",
                "Diga como é a sua casa hoje: própria, alugada, cedida ou emprestada, abrigo ou acolhimento, "
                        + "ou outra situação.");
        adicionar("endereco-fixo", "o que é endereço fixo",
                "Endereço fixo é um lugar onde você mora de forma regular. Se não tem, tudo bem: vamos pedir uma "
                        + "referência de onde podemos te encontrar.");
        adicionar("contato-referencia", "o que é contato de referência",
                "É uma pessoa de confiança que pode receber um recado para você, caso você não tenha telefone "
                        + "ou e-mail próprio.");
        adicionar("finalidade-cadastro", "para que serve este cadastro",
                "Este cadastro reúne informações básicas para a Defensoria organizar o atendimento. Ele não "
                        + "decide se você será atendido e não dá parecer jurídico. É um protótipo com dados fictícios.");
    }

    private void adicionar(String id, String descricao, String texto) {
        verbetes.put(id, new Verbete(descricao, texto));
    }

    /** Tópicos enviados ao modelo: só id e descrição curta. */
    public List<Topico> topicos() {
        return verbetes.entrySet().stream()
                .map(e -> new Topico(e.getKey(), e.getValue().descricao()))
                .toList();
    }

    public Optional<String> texto(String id) {
        return Optional.ofNullable(id).map(verbetes::get).map(Verbete::texto);
    }
}
