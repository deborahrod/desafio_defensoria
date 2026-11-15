import estilos from './PaginaInicial.module.css'

const PORTAL = 'https://www2.defensoria.go.def.br/ser-atendido'

interface Props {
  aoComecar: () => void
  enviando: boolean
  falha: string | null
}

const BLOCOS = [
  {
    titulo: 'Renda e vulnerabilidade',
    itens: [
      'Pessoas de baixa renda, sem recursos para pagar um advogado particular, com atendimento prioritário.',
      'Pessoas em situação de rua, migrantes, refugiados e outras em vulnerabilidade social.',
    ],
  },
  {
    titulo: 'Proteção de direitos',
    itens: [
      'Mulheres em situação de violência: apoio em processos e medidas protetivas.',
      'Crianças, adolescentes e idosos: guarda, visitação, adoção e direitos da terceira idade.',
      'Pessoas com deficiência e consumidores hipossuficientes.',
    ],
  },
  {
    titulo: 'Sistema de justiça',
    itens: [
      'Presos e seus familiares: orientação jurídica e acompanhamento de processos penais.',
      'A Defensoria busca igualdade de acesso à justiça, sem distinção de origem, gênero, raça ou condição social.',
    ],
  },
]

export default function PaginaInicial({ aoComecar, enviando, falha }: Props) {
  return (
    <div className={estilos.pagina}>
      <section aria-labelledby="titulo-quem">
        <h2 id="titulo-quem" className={estilos.titulo}>
          Quem pode ser atendido
        </h2>
        <p className={estilos.introducao}>
          A Defensoria Pública garante assistência jurídica gratuita a quem não tem condições de pagar um advogado. Veja alguns dos grupos atendidos.
        </p>
        <ul className={estilos.blocos}>
          {BLOCOS.map((bloco) => (
            <li key={bloco.titulo} className={estilos.bloco}>
              <h3 className={estilos.nomeBloco}>{bloco.titulo}</h3>
              <ul className={estilos.itens}>
                {bloco.itens.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
        <p className={estilos.criterios}>
          Os critérios completos e a decisão sobre o atendimento são da Defensoria.{' '}
          <a href={PORTAL} target="_blank" rel="noreferrer">
            Veja a página oficial (abre em outra aba)
          </a>
          .
        </p>
      </section>

      <section className={estilos.antes} aria-labelledby="titulo-antes">
        <h2 id="titulo-antes" className={estilos.tituloAntes}>
          Antes de começar
        </h2>
        <ul className={estilos.itens}>
          <li>Uma pergunta por vez, em português simples. No final você confere tudo e confirma.</li>
          <li>Esta é uma demonstração técnica: use apenas dados fictícios. O cadastro não tem validade oficial.</li>
          <li>Se a assistência por IA estiver ativada, você escolhe durante o cadastro se quer usá-la.</li>
        </ul>
        <button type="button" className={estilos.comecar} disabled={enviando} onClick={aoComecar}>
          Começar
        </button>
        {falha && (
          <p role="alert" className={estilos.erro}>
            {falha}
          </p>
        )}
      </section>
    </div>
  )
}
