import { useId, useState } from 'react'
import type { Entrada, EstadoConversa, ItemResumo } from '../api/tipos.ts'
import EntradaConversa from './EntradaConversa.tsx'
import estilos from './PainelRespostas.module.css'

interface Props {
  estado: EstadoConversa
  desabilitado: boolean
  aoEditar: (item: ItemResumo, entrada: Entrada, rotulo: string) => void
}

/** Respostas já aceitas, ao lado da conversa, cada uma com a opção de editar. */
export default function PainelRespostas({ estado, desabilitado, aoEditar }: Props) {
  const [aberto, setAberto] = useState(false)
  const [edicao, setEdicao] = useState<{ campo: string; versao: number } | null>(null)
  const idTitulo = useId()
  const idConteudo = useId()

  // Cada alteração aceita gera nova versão, e uma edição aberta em versão antiga deixa de valer.
  const editando = edicao?.versao === estado.versao ? edicao.campo : null
  const total = estado.resumo.reduce((soma, secao) => soma + secao.itens.length, 0)
  // Enquanto o servidor espera uma escolha, outra edição é recusada.
  const bloqueado = desabilitado || estado.estado === 'AGUARDANDO_ESCLARECIMENTO'

  return (
    <aside className={estilos.painel} aria-labelledby={idTitulo} data-aberto={aberto}>
      <div className={estilos.cabecalho}>
        <h2 id={idTitulo} className={estilos.titulo}>
          Suas respostas
        </h2>
        <button type="button" className={estilos.alternar} aria-expanded={aberto} aria-controls={idConteudo} onClick={() => setAberto(!aberto)}>
          {aberto ? 'Ocultar' : 'Mostrar'} ({total})
        </button>
      </div>

      <div id={idConteudo} className={estilos.conteudo}>
        {total === 0 && <p className={estilos.vazio}>As respostas aparecem aqui, e você pode editar qualquer uma.</p>}

        {estado.resumo.map((secao) => (
          <section key={secao.titulo} className={estilos.secao} aria-label={secao.titulo}>
            <h3 className={estilos.nomeSecao}>{secao.titulo}</h3>
            <ul className={estilos.itens}>
              {secao.itens.map((item) => (
                <li key={item.campo} className={estilos.item} data-situacao={item.situacao}>
                  <dl className={estilos.dado}>
                    <dt className={estilos.rotulo}>{item.rotulo}</dt>
                    <dd className={estilos.valor}>{item.valor}</dd>
                  </dl>
                  <button
                    type="button"
                    className={estilos.editar}
                    disabled={bloqueado}
                    aria-expanded={editando === item.campo}
                    aria-label={`Editar ${item.rotulo}`}
                    onClick={() => setEdicao(editando === item.campo ? null : { campo: item.campo, versao: estado.versao })}
                  >
                    Editar
                  </button>
                  {editando === item.campo && (
                    <div className={estilos.edicao}>
                      <p className={estilos.pergunta}>{item.edicao.texto}</p>
                      <EntradaConversa
                        pergunta={item.edicao}
                        desabilitado={bloqueado}
                        rotuloDoCampo={`Nova resposta: ${item.rotulo}`}
                        aoEnviar={(entrada, rotulo) => aoEditar(item, entrada, rotulo)}
                      />
                      <button type="button" className={estilos.cancelar} onClick={() => setEdicao(null)}>
                        Cancelar edição
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </aside>
  )
}
