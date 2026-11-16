package io.github.deborahrod.defensoria.conversa;

import static io.github.deborahrod.defensoria.cadastro.Campo.*;
import static io.github.deborahrod.defensoria.conversa.Pergunta.Alternativa.ausencia;
import static io.github.deborahrod.defensoria.conversa.Pergunta.Alternativa.valor;
import static io.github.deborahrod.defensoria.conversa.Pergunta.Alternativa.valorFixo;
import static io.github.deborahrod.defensoria.conversa.Secao.*;

import io.github.deborahrod.defensoria.cadastro.Campo;
import io.github.deborahrod.defensoria.cadastro.MotivoAusencia;
import io.github.deborahrod.defensoria.cadastro.RascunhoCadastro;
import io.github.deborahrod.defensoria.conversa.Pergunta.Alternativa;
import io.github.deborahrod.defensoria.integracao.PropriedadesLlm;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Sequência de perguntas do cadastro. A etapa atual não é guardada: é sempre a primeira
 * pergunta aplicável ainda sem resposta, o que mantém o fluxo coerente após correções.
 */
@Component
public class RoteiroCadastro {

    public static final String PROPRIA_PESSOA = "PROPRIA_PESSOA";
    public static final String OUTRA_PESSOA = "OUTRA_PESSOA";
    public static final String SIM = "SIM";
    public static final String NAO = "NAO";
    public static final String USAR_IA = "USAR_IA";
    public static final String ESTRUTURADO = "ESTRUTURADO";

    private static final Alternativa NAO_SEI = ausencia("NAO_SEI", "Não sei informar", MotivoAusencia.DESCONHECIDO);
    private static final Alternativa PULAR = ausencia("PULAR", "Pular", MotivoAusencia.OPCIONAL_NAO_PREENCHIDO);

    private static final List<String> UFS = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private final List<Pergunta> perguntas;

    public RoteiroCadastro(PropriedadesLlm llm) {
        this.perguntas = montar(llm.disponivel());
    }

    private static List<Pergunta> montar(boolean iaDisponivel) {
        return List.of(
            Pergunta.de(PARA_QUEM, QUEM_PREENCHE, "Cadastro para", TipoEntrada.ESCOLHA)
                    .perguntar("Este cadastro é para você ou para outra pessoa?")
                    .alternativas(
                            valor(PROPRIA_PESSOA, "Para mim"),
                            valor(OUTRA_PESSOA, "Para outra pessoa")),
            Pergunta.de(NOME_QUEM_PREENCHE, QUEM_PREENCHE, "Nome de quem preenche", TipoEntrada.TEXTO)
                    .perguntar("Qual é o seu nome? Você é quem está preenchendo.")
                    .validar(Validadores::nome, 200)
                    .quando(RoteiroCadastro::paraOutraPessoa),
            Pergunta.de(VINCULO_REPRESENTACAO, QUEM_PREENCHE, "Relação com a pessoa atendida", TipoEntrada.ESCOLHA)
                    .perguntar("Qual é a sua relação com a pessoa que será atendida?")
                    .ajuda("Vamos registrar o que você declarar. Documentos de representação são conferidos "
                            + "no atendimento.")
                    .alternativas(
                            valor("FAMILIAR", "Sou familiar"),
                            valor("AMIZADE", "Sou amigo(a) ou vizinho(a)"),
                            valor("PROFISSIONAL", "Sou profissional que acompanha a pessoa"),
                            valor("RESPONSAVEL_LEGAL", "Sou responsável legal"),
                            valor("OUTRA", "Outra relação"))
                    .quando(RoteiroCadastro::paraOutraPessoa),

            Pergunta.de(NOME, IDENTIFICACAO, "Nome", TipoEntrada.TEXTO)
                    .perguntar("Como você se chama?", "Como se chama a pessoa que será atendida?")
                    .validar(Validadores::nome, 200),
            Pergunta.de(NOME_SOCIAL, IDENTIFICACAO, "Nome social", TipoEntrada.TEXTO)
                    .perguntar("Deseja informar um nome social?", "A pessoa usa um nome social?")
                    .ajuda("Nome social é o nome pelo qual a pessoa se identifica e prefere ser chamada.")
                    .validar(Validadores::nome, 200)
                    .comoOpcional()
                    .alternativas(PULAR),
            Pergunta.de(NASCIMENTO, IDENTIFICACAO, "Data de nascimento", TipoEntrada.DATA)
                    .perguntar("Qual é a sua data de nascimento?", "Qual é a data de nascimento da pessoa?")
                    .validar(Validadores::nascimento, 10)
                    .alternativas(NAO_SEI),
            Pergunta.de(SITUACAO_CPF, IDENTIFICACAO, "Tem CPF para informar", TipoEntrada.ESCOLHA)
                    .perguntar("Você possui o CPF para informar agora?", "Você tem o CPF da pessoa para informar agora?")
                    .ajuda("Se o documento não estiver disponível, a ausência fica registrada para "
                            + "complementação no atendimento.")
                    .alternativas(
                            valor("INFORMADO", "Sim, vou informar"),
                            valor("NAO_POSSUI", "Não possuo CPF"),
                            valor("DESCONHECIDO", "Não sei informar"),
                            valor("NAO_APRESENTADO", "Não estou com o documento")),
            Pergunta.de(CPF, IDENTIFICACAO, "CPF", TipoEntrada.CPF)
                    .perguntar("Informe o seu CPF.", "Informe o CPF da pessoa.")
                    .validar(Validadores::cpf, 14)
                    .quando(rascunho -> rascunho.tem(SITUACAO_CPF, "INFORMADO")),

            Pergunta.de(FORMA_CONTATO, CONTATO, "Forma de contato", TipoEntrada.ESCOLHA)
                    .perguntar("Como podemos entrar em contato com você?", "Como podemos entrar em contato com a pessoa?")
                    .alternativas(
                            valor("TELEFONE", "Por telefone"),
                            valor("EMAIL", "Por e-mail"),
                            valor("REFERENCIA", "Pelo contato de outra pessoa"),
                            valor("INDISPONIVEL", "Não há contato disponível agora")),
            Pergunta.de(TELEFONE, CONTATO, "Telefone", TipoEntrada.TELEFONE)
                    .perguntar("Qual número podemos usar?")
                    .ajuda("Informe com DDD. Pode ser um número compartilhado.")
                    .validar(Validadores::telefone, 20)
                    .alternativas(ausencia("SEM_TELEFONE", "Não tenho telefone", MotivoAusencia.NAO_POSSUI))
                    .quando(rascunho -> rascunho.tem(FORMA_CONTATO, "TELEFONE")),
            Pergunta.de(EMAIL, CONTATO, "E-mail", TipoEntrada.EMAIL)
                    .perguntar("Qual é o e-mail?")
                    .validar(Validadores::email, 254)
                    .quando(rascunho -> rascunho.tem(FORMA_CONTATO, "EMAIL")),
            Pergunta.de(EMAIL, CONTATO, "E-mail", TipoEntrada.EMAIL)
                    .perguntar("Há também um e-mail para contato?")
                    .validar(Validadores::email, 254)
                    .comoOpcional()
                    .alternativas(PULAR)
                    .quando(rascunho -> rascunho.tem(FORMA_CONTATO, "TELEFONE")),
            Pergunta.de(REFERENCIA_NOME, CONTATO, "Pessoa de contato", TipoEntrada.TEXTO)
                    .perguntar("Qual é o nome da pessoa de contato?")
                    .validar(Validadores::nome, 200)
                    .quando(RoteiroCadastro::contatoPorReferencia),
            Pergunta.de(REFERENCIA_VINCULO, CONTATO, "Relação da pessoa de contato", TipoEntrada.TEXTO)
                    .perguntar("Qual é a relação dessa pessoa com você?", "Qual é a relação dessa pessoa com quem será atendido?")
                    .ajuda("Por exemplo: vizinha, irmão, amiga.")
                    .validar(Validadores.texto(100), 100)
                    .quando(RoteiroCadastro::contatoPorReferencia),
            Pergunta.de(REFERENCIA_CANAL, CONTATO, "Telefone ou e-mail de contato", TipoEntrada.TEXTO)
                    .perguntar("Qual telefone ou e-mail dessa pessoa podemos usar?")
                    .validar(Validadores::telefoneOuEmail, 254)
                    .quando(RoteiroCadastro::contatoPorReferencia),

            Pergunta.de(AVISO_IA, LOCALIZACAO, "Assistência por IA", TipoEntrada.ESCOLHA)
                    .perguntar("Posso usar inteligência artificial para entender melhor as próximas respostas escritas?")
                    .ajuda("O que você escrever sobre município e ocupação será enviado a um serviço externo de IA, "
                            + "que sugere o que preencher. CPF, nascimento, telefone, e-mail e endereço não são "
                            + "enviados. Você revisa tudo antes de confirmar e pode seguir com perguntas diretas.")
                    .alternativas(
                            valor(USAR_IA, "Usar assistência por IA"),
                            valor(ESTRUTURADO, "Continuar com perguntas estruturadas"))
                    .foraDoResumo()
                    .quando(rascunho -> iaDisponivel),
            Pergunta.de(RELATO, LOCALIZACAO, "Relato", TipoEntrada.RELATO)
                    .perguntar("Se preferir, conte em poucas linhas onde você mora, o que faz e quem mora com você. "
                            + "Eu preencho vários campos de uma vez e você confere.")
                    .ajuda("Não conte detalhes do seu caso: só esses dados. Máximo de 400 caracteres. "
                            + "CPF, nascimento, telefone e e-mail são pedidos à parte.")
                    .validar(Validadores.texto(400), 400)
                    .comoOpcional()
                    .alternativas(ausencia("PULAR_RELATO", "Prefiro responder pergunta por pergunta",
                            MotivoAusencia.OPCIONAL_NAO_PREENCHIDO))
                    .foraDoResumo()
                    .quando(rascunho -> rascunho.tem(AVISO_IA, USAR_IA)),
            Pergunta.de(MUNICIPIO, LOCALIZACAO, "Município ou localidade", TipoEntrada.TEXTO)
                    .interpretavelPorIa("município ou localidade onde a pessoa está")
                    .noRelato("município ou localidade onde a pessoa está")
                    .perguntar("Em qual município ou localidade você está?", "Em qual município ou localidade a pessoa está?")
                    .validar(Validadores.texto(120), 120)
                    .alternativas(NAO_SEI),
            Pergunta.de(UF, LOCALIZACAO, "Estado", TipoEntrada.LISTA)
                    .sugeridaCom(MUNICIPIO)
                    .noRelato("sigla do estado (UF) do município")
                    .perguntar("Em qual estado fica?")
                    .alternativas(Stream.concat(
                            UFS.stream().map(uf -> valor(uf, uf)), Stream.of(NAO_SEI)).toArray(Alternativa[]::new))
                    .quando(rascunho -> rascunho.valor(MUNICIPIO).isPresent()),
            Pergunta.de(ENDERECO_FIXO, LOCALIZACAO, "Tem endereço fixo", TipoEntrada.ESCOLHA)
                    .perguntar("Você tem endereço fixo?", "A pessoa tem endereço fixo?")
                    .alternativas(valor(SIM, "Sim"), valor(NAO, "Não")),
            Pergunta.de(CEP, LOCALIZACAO, "CEP", TipoEntrada.CEP)
                    .perguntar("Sabe o CEP?")
                    .ajuda("Com o CEP eu busco rua, bairro e município em um serviço público de endereços "
                            + "(ViaCEP ou BrasilAPI). Só o CEP é enviado. Você confere o resultado e, se não "
                            + "achar, preenche o endereço à mão.")
                    .validar(Validadores::cep, 9)
                    .comoOpcional()
                    .alternativas(PULAR)
                    .quando(RoteiroCadastro::comEnderecoFixo),
            Pergunta.de(LOGRADOURO, LOCALIZACAO, "Logradouro", TipoEntrada.TEXTO)
                    .perguntar("Qual é a rua, avenida ou logradouro?")
                    .validar(Validadores.texto(200), 200)
                    .alternativas(NAO_SEI)
                    .quando(RoteiroCadastro::comEnderecoFixo),
            Pergunta.de(NUMERO, LOCALIZACAO, "Número", TipoEntrada.TEXTO)
                    .perguntar("Qual é o número?")
                    .validar(Validadores.texto(20), 20)
                    .alternativas(ausencia("SEM_NUMERO", "Sem número", MotivoAusencia.SEM_NUMERO))
                    .quando(RoteiroCadastro::comEnderecoFixo),
            Pergunta.de(BAIRRO, LOCALIZACAO, "Bairro ou setor", TipoEntrada.TEXTO)
                    .perguntar("Qual é o bairro ou setor?")
                    .validar(Validadores.texto(120), 120)
                    .alternativas(NAO_SEI)
                    .quando(RoteiroCadastro::comEnderecoFixo),
            Pergunta.de(COMPLEMENTO, LOCALIZACAO, "Complemento", TipoEntrada.TEXTO)
                    .perguntar("Tem complemento?")
                    .ajuda("Por exemplo: quadra, lote, bloco ou apartamento.")
                    .validar(Validadores.texto(100), 100)
                    .comoOpcional()
                    .alternativas(PULAR)
                    .quando(RoteiroCadastro::comEnderecoFixo),
            Pergunta.de(REFERENCIA_LOCALIZACAO, LOCALIZACAO, "Referência de localização", TipoEntrada.TEXTO)
                    .perguntar(
                            "Onde você costuma ficar ou onde podemos te encontrar?",
                            "Onde a pessoa costuma ficar ou pode ser encontrada?")
                    .ajuda("Pode ser um ponto de referência, um abrigo ou a casa de alguém.")
                    .validar(Validadores.texto(300), 300)
                    .alternativas(ausencia("SEM_REFERENCIA", "Não tenho referência", MotivoAusencia.INDISPONIVEL))
                    .quando(rascunho -> rascunho.tem(ENDERECO_FIXO, NAO)),

            Pergunta.de(OCUPACAO, SOCIOECONOMICO, "Ocupação", TipoEntrada.TEXTO)
                    .interpretavelPorIa("ocupação ou situação de trabalho atual, como desempregada, autônomo ou aposentada")
                    .noRelato("ocupação ou situação de trabalho atual, como desempregada, autônomo ou aposentada")
                    .perguntar("Qual é a sua ocupação ou trabalho atual?", "Qual é a ocupação ou trabalho atual da pessoa?")
                    .ajuda("Vale responder, por exemplo, desempregado(a), do lar, aposentado(a) ou autônomo(a).")
                    .validar(Validadores.texto(120), 120)
                    .alternativas(NAO_SEI),
            Pergunta.de(RENDA, SOCIOECONOMICO, "Renda mensal declarada", TipoEntrada.VALOR)
                    .noRelato("renda mensal da pessoa, em reais, como número")
                    .perguntar("Você tem alguma renda mensal para informar?", "A pessoa tem alguma renda mensal para informar?")
                    .ajuda("Informe quanto recebe por mês, em reais. Vale salário, benefício, pensão ou bicos.")
                    .validar(Validadores::valorEmReais, 20)
                    .alternativas(valorFixo("SEM_RENDA", "Não tenho renda", "0.00"), NAO_SEI),
            Pergunta.de(QUANTIDADE_MEMBROS, SOCIOECONOMICO, "Pessoas na mesma casa", TipoEntrada.NUMERO)
                    .noRelato("quantas pessoas moram na mesma casa, contando a própria pessoa (número inteiro)")
                    .perguntar(
                            "Contando com você, quantas pessoas vivem na mesma casa ou no mesmo núcleo familiar?",
                            "Contando com a pessoa, quantos vivem na mesma casa ou no mesmo núcleo familiar?")
                    .validar(Validadores::pessoasNoNucleo, 2)
                    .alternativas(NAO_SEI),
            Pergunta.de(DEPENDENTES, SOCIOECONOMICO, "Dependentes", TipoEntrada.NUMERO)
                    .noRelato("quantas dessas pessoas dependem financeiramente da pessoa (número inteiro)")
                    .perguntar(
                            "Quantas dessas pessoas dependem de você financeiramente?",
                            "Quantas dessas pessoas dependem financeiramente de quem será atendido?")
                    .validar(Validadores::dependentes, 2)
                    .alternativas(NAO_SEI)
                    .quando(rascunho -> !rascunho.tem(QUANTIDADE_MEMBROS, "1")),
            Pergunta.de(SITUACAO_MORADIA, SOCIOECONOMICO, "Moradia", TipoEntrada.ESCOLHA)
                    .noRelato("situação da moradia atual")
                    .perguntar("Como é a sua moradia hoje?", "Como é a moradia da pessoa hoje?")
                    .alternativas(RoteiroCadastro::moradias));
    }

    public Optional<Pergunta> proxima(RascunhoCadastro rascunho) {
        return aplicaveis(rascunho).filter(pergunta -> !rascunho.respondido(pergunta.campo())).findFirst();
    }

    public Optional<Pergunta> de(Campo campo, RascunhoCadastro rascunho) {
        return aplicaveis(rascunho).filter(pergunta -> pergunta.campo() == campo).findFirst();
    }

    /** Todas as perguntas, na ordem do roteiro, estejam ou não aplicáveis ao rascunho atual. */
    public Stream<Pergunta> todas() {
        return perguntas.stream();
    }

    public Stream<Pergunta> aplicaveis(RascunhoCadastro rascunho) {
        return perguntas.stream().filter(pergunta -> pergunta.aplicavel(rascunho));
    }

    public String rotulo(Campo campo) {
        return perguntas.stream()
                .filter(pergunta -> pergunta.campo() == campo)
                .map(Pergunta::rotulo)
                .findFirst()
                .orElseThrow();
    }

    public static boolean paraOutraPessoa(RascunhoCadastro rascunho) {
        return rascunho.tem(PARA_QUEM, OUTRA_PESSOA);
    }

    private static boolean contatoPorReferencia(RascunhoCadastro rascunho) {
        return rascunho.tem(FORMA_CONTATO, "REFERENCIA");
    }

    private static boolean comEnderecoFixo(RascunhoCadastro rascunho) {
        return rascunho.tem(ENDERECO_FIXO, SIM);
    }

    private static List<Alternativa> moradias(RascunhoCadastro rascunho) {
        if (rascunho.tem(ENDERECO_FIXO, NAO)) {
            return List.of(
                    valor("ACOLHIMENTO", "Em abrigo ou acolhimento"),
                    valor("SEM_ENDERECO_FIXO", "Sem moradia fixa"),
                    valor("OUTRA", "Outra situação"),
                    NAO_SEI);
        }
        return List.of(
                valor("PROPRIA", "Própria"),
                valor("ALUGADA", "Alugada"),
                valor("CEDIDA", "Cedida ou emprestada"),
                valor("ACOLHIMENTO", "Abrigo ou acolhimento"),
                valor("OUTRA", "Outra situação"),
                NAO_SEI);
    }
}
