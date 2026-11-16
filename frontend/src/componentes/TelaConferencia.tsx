import { useEffect, useId, useRef, useState } from 'react'
import type { Entrada, EstadoConversa, ItemResumo } from '../api/tipos.ts'
import EntradaConversa from './EntradaConversa.tsx'
import estilos from './TelaConferencia.module.css'

interface Props {
  estado: EstadoConversa
  enviando: boolean
  falha: string | null
  recusa: string | null
  aoCorrigir: (item: ItemResumo, entrada: Entrada, rotulo: string) => void
  aoConfirmar: () => void
  aoTentarNovamente: () => void
  aoCancelar: () => void
}

const SELO: Record<ItemResumo['situacao'], string | null> = {
  INFORMADO: null,
  NAO_INFORMADO: 'Ausência declarada',
  OPCIONAL_NAO_PREENCHIDO: 'Opcional, não preenchido',
}

/**
 * Tela própria de conferência, fora da conversa. Mostra tudo o que foi respondido por seção, destaca o que
 * a pessoa declarou não ter e só aqui existe o botão que grava o cadastro.
 */
export default function TelaConferencia({ estado, enviando, falha, recusa, aoCorrigir, aoConfirmar, aoTentarNovamente, aoCancelar }: Props) {
  const idTitulo = useId()
  const titulo = useRef<HTMLHeadingElement>(null)
  const [edicao, setEdicao] = useState<{ campo: string; versao: number } | null>(null)
  const [saindo, setSaindo] = useState(false)

  // Ao abrir a conferência e a cada correção aceita, o foco vai ao título: quem usa só o teclado não o perde.
  useEffect(() => titulo.current?.focus(), [estado.versao])

  // Cada correção aceita gera nova versão, e um editor aberto em versão antiga deixa de valer.
  const editando = edicao?.versao === estado.versao ? edicao.campo : null

  return (
    <section className={estilos.tela} aria-labelledby={idTitulo}>
      <header className={estilos.cabecalho}>
        <h2 id={idTitulo} ref={titulo} tabIndex={-1} className={estilos.titulo}>
          Revise seus dados
        </h2>
        <p>
          Confira cada informação antes de confirmar. Nada foi gravado ainda. Se algo estiver errado, use{' '}
          <span className={estilos.destaque}>Corrigir</span> no próprio item. Confirmar grava o cadastro deste protótipo e não significa que o atendimento foi
          aprovado.
        </p>
      </header>

      {estado.pendencias.length > 0 && (
        <div className={estilos.pendencias} role="note" aria-label="Informações pendentes">
          <h3>Informações pendentes</h3>
          <ul>
            {estado.pendencias.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
          <p>Elas ficam registradas para serem complementadas no atendimento.</p>
        </div>
      )}

      <div className={estilos.cartoes}>
        {estado.resumo.map((secao) => (
          <section key={secao.titulo} className={estilos.cartao} aria-label={secao.titulo}>
            <h3 className={estilos.nomeSecao}>{secao.titulo}</h3>
            <ul className={estilos.itens}>
              {secao.itens.map((item) => {
                const selo = SELO[item.situacao]
                return (
                  <li key={item.campo} className={estilos.item} data-situacao={item.situacao}>
                    <dl className={estilos.dado}>
                      <dt className={estilos.rotulo}>{item.rotulo}</dt>
                      <dd className={estilos.valor}>
                        {item.valor}
                        {selo && <span className={estilos.selo}>{selo}</span>}
                      </dd>
                    </dl>
                    <button
                      type="button"
                      className={estilos.corrigir}
                      disabled={enviando || estado.estado !== 'EM_REVISAO'}
                      aria-expanded={editando === item.campo}
                      aria-label={`Corrigir ${item.rotulo}`}
                      onClick={() => setEdicao(editando === item.campo ? null : { campo: item.campo, versao: estado.versao })}
                    >
                      Corrigir
                    </button>
                    {editando === item.campo && (
                      <div className={estilos.edicao}>
                        <p className={estilos.pergunta}>{item.edicao.texto}</p>
                        <EntradaConversa
                          pergunta={item.edicao}
                          desabilitado={enviando}
                          rotuloDoCampo={`Nova resposta: ${item.rotulo}`}
                          aoEnviar={(entrada, rotulo) => aoCorrigir(item, entrada, rotulo)}
                        />
                        <button type="button" className={estilos.cancelarEdicao} onClick={() => setEdicao(null)}>
                          Cancelar correção
                        </button>
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          </section>
        ))}
      </div>

      {enviando && (
        <p className={estilos.status} role="status">
          Enviando…
        </p>
      )}
      {recusa && (
        <p role="alert" className={estilos.erro}>
          {recusa}
        </p>
      )}
      {falha && (
        <div role="alert" className={estilos.erro}>
          <p>{falha}</p>
          <button type="button" className={estilos.secundario} disabled={enviando} onClick={aoTentarNovamente}>
            Tentar novamente
          </button>
        </div>
      )}

      <div className={estilos.acoes}>
        <button type="button" className={estilos.confirmar} disabled={enviando} onClick={aoConfirmar}>
          Confirmar cadastro
        </button>
        {saindo ? (
          <div className={estilos.saida}>
            <p>Sair agora apaga o que você respondeu, e nada será gravado. Deseja sair?</p>
            <button type="button" className={estilos.secundario} onClick={() => { setSaindo(false); aoCancelar() }}>
              Sim, sair e apagar
            </button>
            <button type="button" className={estilos.secundario} onClick={() => setSaindo(false)}>
              Continuar a revisão
            </button>
          </div>
        ) : (
          <button type="button" className={estilos.link} onClick={() => setSaindo(true)}>
            Cancelar cadastro
          </button>
        )}
      </div>
    </section>
  )
}
