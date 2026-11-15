import { useId, useState } from 'react'
import type { Entrada, EstadoConversa, ItemResumo } from '../api/tipos.ts'
import EntradaConversa from './EntradaConversa.tsx'
import estilos from './ResumoCadastro.module.css'

interface Props {
  estado: EstadoConversa
  desabilitado: boolean
  aoCorrigir: (item: ItemResumo, entrada: Entrada, rotulo: string) => void
  aoConfirmar: () => void
}

/** Revisão final: valores explícitos por seção, ação Corrigir em cada item e confirmação própria. */
export default function ResumoCadastro({ estado, desabilitado, aoCorrigir, aoConfirmar }: Props) {
  const [edicao, setEdicao] = useState<{ campo: string; versao: number } | null>(null)
  const idTitulo = useId()

  // Cada alteração aceita gera nova versão, e uma edição aberta em versão antiga deixa de valer.
  const editando = edicao?.versao === estado.versao ? edicao.campo : null
  const abrir = (campo: string) => setEdicao(editando === campo ? null : { campo, versao: estado.versao })

  return (
    <section className={estilos.resumo} aria-labelledby={idTitulo}>
      <h2 id={idTitulo} className={estilos.titulo}>
        Revise seus dados
      </h2>
      <p>
        Confira se está tudo certo. Se algo estiver errado, use Corrigir no próprio item. Confirmar grava o cadastro deste
        protótipo e não significa que o atendimento foi aprovado.
      </p>

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

      {estado.resumo.map((secao) => (
        <section key={secao.titulo} className={estilos.cartao} aria-label={secao.titulo}>
          <h3 className={estilos.secao}>{secao.titulo}</h3>
          <dl className={estilos.itens}>
            {secao.itens.map((item) => (
              <div key={item.campo} className={estilos.item} data-situacao={item.situacao}>
                <dt className={estilos.rotulo}>{item.rotulo}</dt>
                <dd className={estilos.valor}>{item.valor}</dd>
                <dd className={estilos.acao}>
                  <button
                    type="button"
                    className={estilos.corrigir}
                    disabled={desabilitado}
                    aria-expanded={editando === item.campo}
                    aria-label={`Corrigir ${item.rotulo}`}
                    onClick={() => abrir(item.campo)}
                  >
                    Corrigir
                  </button>
                </dd>
                {editando === item.campo && (
                  <div className={estilos.edicao}>
                    <p className={estilos.pergunta}>{item.edicao.texto}</p>
                    <EntradaConversa
                      pergunta={item.edicao}
                      desabilitado={desabilitado}
                      aoEnviar={(entrada, rotulo) => aoCorrigir(item, entrada, rotulo)}
                    />
                    <button type="button" className={estilos.cancelar} onClick={() => setEdicao(null)}>
                      Cancelar correção
                    </button>
                  </div>
                )}
              </div>
            ))}
          </dl>
        </section>
      ))}

      <button type="button" className={estilos.confirmar} disabled={desabilitado} onClick={aoConfirmar}>
        Confirmar cadastro
      </button>
    </section>
  )
}
