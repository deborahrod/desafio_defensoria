import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import type { Entrada, ProximaPergunta, TipoEntrada } from '../api/tipos.ts'
import estilos from './EntradaConversa.module.css'

interface Props {
  pergunta: ProximaPergunta
  desabilitado: boolean
  aoEnviar: (entrada: Entrada, rotulo: string) => void
  /** Nome do campo para leitores de tela. Distingue o campo de edição do campo da conversa. */
  rotuloDoCampo?: string
}

const ATRIBUTOS: Partial<Record<TipoEntrada, React.InputHTMLAttributes<HTMLInputElement>>> = {
  DATA: { type: 'date', max: new Date().toISOString().slice(0, 10) },
  CPF: { type: 'text', inputMode: 'numeric', autoComplete: 'off', maxLength: 14 },
  TELEFONE: { type: 'tel', inputMode: 'tel', autoComplete: 'tel' },
  EMAIL: { type: 'email', inputMode: 'email', autoComplete: 'email' },
  CEP: { type: 'text', inputMode: 'numeric', autoComplete: 'postal-code', maxLength: 9 },
  VALOR: { type: 'text', inputMode: 'decimal', autoComplete: 'off' },
  NUMERO: { type: 'text', inputMode: 'numeric', autoComplete: 'off', maxLength: 3 },
}

function exibir(tipo: TipoEntrada, valor: string): string {
  if (tipo !== 'DATA') return valor
  const [ano, mes, dia] = valor.split('-')
  return `${dia}/${mes}/${ano}`
}

/** Entrada adequada ao tipo da pergunta. Os botões de ausência valem para qualquer tipo. */
export default function EntradaConversa({ pergunta, desabilitado, aoEnviar, rotuloDoCampo = 'Sua resposta' }: Props) {
  const [valor, setValor] = useState('')
  const raiz = useRef<HTMLDivElement>(null)
  const idCampo = useId()
  const idAjuda = useId()
  const idContador = useId()
  const { tipoEntrada: tipo, opcoes } = pergunta
  const porBotoes = tipo === 'ESCOLHA'
  const porLista = tipo === 'LISTA'
  const relato = tipo === 'RELATO'
  const descritoPor = [pergunta.ajuda ? idAjuda : '', relato ? idContador : ''].filter(Boolean).join(' ') || undefined

  useEffect(() => {
    raiz.current?.querySelector<HTMLElement>('input, select, textarea, button')?.focus()
  }, [pergunta.campo])

  const escolher = (id: string, rotulo: string) => aoEnviar({ opcao: id }, rotulo)

  const enviarValor = (evento: FormEvent) => {
    evento.preventDefault()
    const limpo = valor.trim()
    if (!limpo || desabilitado) return
    if (porLista) {
      const escolhida = opcoes.find((o) => o.id === limpo)
      if (escolhida) escolher(escolhida.id, escolhida.rotulo)
      return
    }
    aoEnviar({ valor: limpo }, exibir(tipo, limpo))
    // O relato fica no campo: se for recusado, a pessoa corrige em vez de reescrever tudo.
    if (!relato) setValor('')
  }

  // Em texto longo o Enter quebra a linha; Ctrl+Enter envia.
  const enviarComAtalho = (evento: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (evento.key === 'Enter' && (evento.ctrlKey || evento.metaKey)) {
      evento.preventDefault()
      evento.currentTarget.form?.requestSubmit()
    }
  }

  return (
    <div ref={raiz} className={estilos.entrada}>
      {pergunta.ajuda && (
        <p id={idAjuda} className={estilos.ajuda}>
          {pergunta.ajuda}
        </p>
      )}

      {pergunta.itens && pergunta.itens.length > 0 && (
        <ul className={estilos.itens} aria-label="Dados entendidos">
          {pergunta.itens.map((item) => (
            <li key={item.rotulo}>
              <span className={estilos.itemRotulo}>{item.rotulo}:</span> {item.valor}
            </li>
          ))}
        </ul>
      )}

      {porBotoes && (
        <div className={estilos.opcoes} role="group" aria-label="Opções de resposta">
          {opcoes.map((o) => (
            <button key={o.id} type="button" className={estilos.opcao} disabled={desabilitado} onClick={() => escolher(o.id, o.rotulo)}>
              {o.rotulo}
            </button>
          ))}
        </div>
      )}

      {!porBotoes && (
        <form className={estilos.formulario} onSubmit={enviarValor}>
          <label htmlFor={idCampo} className={estilos.rotulo}>
            {rotuloDoCampo}
          </label>
          <div className={estilos.linha}>
            {porLista ? (
              <select id={idCampo} className={estilos.campo} value={valor} disabled={desabilitado} onChange={(e) => setValor(e.target.value)}>
                <option value="">Escolha uma opção</option>
                {opcoes.map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.rotulo}
                  </option>
                ))}
              </select>
            ) : relato ? (
              <textarea
                id={idCampo}
                className={`${estilos.campo} ${estilos.relato}`}
                rows={4}
                maxLength={pergunta.tamanhoMaximo ?? undefined}
                value={valor}
                disabled={desabilitado}
                aria-describedby={descritoPor}
                onChange={(e) => setValor(e.target.value)}
                onKeyDown={enviarComAtalho}
              />
            ) : (
              <input
                id={idCampo}
                className={estilos.campo}
                type="text"
                maxLength={pergunta.tamanhoMaximo ?? undefined}
                {...ATRIBUTOS[tipo]}
                value={valor}
                disabled={desabilitado}
                aria-describedby={descritoPor}
                onChange={(e) => setValor(e.target.value)}
              />
            )}
            <button type="submit" className={estilos.enviar} disabled={desabilitado || !valor.trim()}>
              Enviar
            </button>
          </div>
          {relato && (
            <p id={idContador} className={estilos.contador}>
              {valor.length} de {pergunta.tamanhoMaximo} caracteres. Ctrl+Enter envia.
            </p>
          )}
        </form>
      )}

      {!porBotoes && !porLista && opcoes.length > 0 && (
        <div className={estilos.opcoes} role="group" aria-label="Outras respostas">
          {opcoes.map((o) => (
            <button key={o.id} type="button" className={estilos.alternativa} disabled={desabilitado} onClick={() => escolher(o.id, o.rotulo)}>
              {o.rotulo}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
