import { useEffect, useId, useRef } from 'react'
import type { EstadoConversa } from '../api/tipos.ts'
import estilos from './RevisaoFinal.module.css'

interface Props {
  estado: EstadoConversa
  desabilitado: boolean
  aoConfirmar: () => void
}

/** Última etapa: o que falta conferir e o botão próprio de confirmação. Os dados ficam no painel ao lado. */
export default function RevisaoFinal({ estado, desabilitado, aoConfirmar }: Props) {
  const idTitulo = useId()
  const titulo = useRef<HTMLHeadingElement>(null)

  // Ao abrir a revisão e a cada alteração aceita, o foco vai ao título: quem usa só o teclado não o perde.
  useEffect(() => titulo.current?.focus(), [estado.versao])

  return (
    <section className={estilos.revisao} aria-labelledby={idTitulo}>
      <h2 id={idTitulo} ref={titulo} tabIndex={-1} className={estilos.titulo}>
        Revise seus dados
      </h2>
      <p>
        Confira as respostas no painel <span className={estilos.destaque}>Suas respostas</span>. Se algo estiver errado, use Editar no próprio item. Confirmar
        grava o cadastro deste protótipo e não significa que o atendimento foi aprovado.
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

      <button type="button" className={estilos.confirmar} disabled={desabilitado} onClick={aoConfirmar}>
        Confirmar cadastro
      </button>
    </section>
  )
}
