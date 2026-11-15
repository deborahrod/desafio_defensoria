import { useEffect, useRef } from 'react'
import type { Comprovante } from '../api/tipos.ts'
import estilos from './ComprovanteCadastro.module.css'

interface Props {
  comprovante: Comprovante
  desabilitado: boolean
  aoEncerrar: () => void
}

const FORMATO = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'long',
  timeStyle: 'short',
  timeZone: 'America/Sao_Paulo',
})

export default function ComprovanteCadastro({ comprovante, desabilitado, aoEncerrar }: Props) {
  const titulo = useRef<HTMLHeadingElement>(null)

  useEffect(() => titulo.current?.focus(), [])

  return (
    <section className={estilos.comprovante} aria-labelledby="titulo-comprovante">
      <h2 id="titulo-comprovante" ref={titulo} tabIndex={-1} className={estilos.titulo}>
        Cadastro gravado
      </h2>
      <dl className={estilos.dados}>
        <dt>Identificador da operação</dt>
        <dd className={estilos.identificador}>{comprovante.identificador}</dd>
        <dt>Data e hora</dt>
        <dd>{FORMATO.format(new Date(comprovante.confirmadoEm))}</dd>
      </dl>
      {comprovante.comPendencias && (
        <p>O cadastro foi gravado com informações pendentes. Elas ficam registradas para serem complementadas no atendimento.</p>
      )}
      <p className={estilos.aviso}>
        Este é o comprovante de um protótipo com dados fictícios. Não é um protocolo da Defensoria, não promete atendimento,
        agendamento ou vaga e não serve para consultar o cadastro depois.
      </p>
      <button type="button" className={estilos.encerrar} disabled={desabilitado} onClick={aoEncerrar}>
        Encerrar e limpar a sessão
      </button>
    </section>
  )
}
