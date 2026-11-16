import { useState } from 'react'
import type { useConversa } from '../conversa/useConversa.ts'
import estilos from './ChatCadastro.module.css'
import EntradaConversa from './EntradaConversa.tsx'

/** Coluna da conversa: mensagens, a pergunta atual e os avisos de envio. A conferência final tem tela própria. */
export default function ChatCadastro({ conversa }: { conversa: ReturnType<typeof useConversa> }) {
  const { estado, transcricao, enviando, falha, recusa, encerrada } = conversa
  const [confirmandoSaida, setConfirmandoSaida] = useState(false)

  const pergunta = estado?.proximaPergunta

  return (
    <section className={estilos.chat} aria-label="Conversa do cadastro">
      {estado && (
        <p className={estilos.modo}>
          {estado.modoInteracao === 'ASSISTIDO_POR_IA'
            ? 'Assistência por IA ativa: o que você escrever sobre município e ocupação é interpretado por um serviço externo.'
            : 'Perguntas diretas, sem uso de IA.'}
        </p>
      )}

      <div role="log" aria-live="polite" aria-label="Mensagens">
        <ol className={estilos.mensagens}>
          {transcricao.map((m) => (
            <li key={m.id} className={`${estilos.mensagem} ${m.autor === 'pessoa' ? estilos.pessoa : estilos.sistema} ${m.erro ? estilos.recusada : ''}`}>
              <span className={estilos.autor}>{m.autor === 'pessoa' ? 'Você' : 'Defensoria'}:</span> {m.texto}
            </li>
          ))}
        </ol>
      </div>

      {encerrada ? (
        <div role="alert" className={estilos.aviso}>
          <p>{encerrada}</p>
          <button type="button" className={estilos.comecar} disabled={enviando} onClick={conversa.iniciar}>
            Começar um novo cadastro
          </button>
        </div>
      ) : pergunta ? (
        <EntradaConversa key={`${pergunta.campo}-${estado?.versao}`} pergunta={pergunta} desabilitado={enviando} aoEnviar={conversa.enviar} />
      ) : null}

      {enviando && (
        <p className={estilos.status} role="status">
          {estado?.modoInteracao === 'ASSISTIDO_POR_IA' ? 'Enviando… a interpretação por IA pode levar alguns segundos.' : 'Enviando…'}
        </p>
      )}
      {recusa && <p role="alert" className={estilos.erro}>{recusa}</p>}
      {falha && (
        <div role="alert" className={estilos.erro}>
          <p>{falha}</p>
          <button type="button" className={estilos.secundario} disabled={enviando} onClick={conversa.tentarNovamente}>
            Tentar novamente
          </button>
        </div>
      )}

      {!encerrada && (
        <div className={estilos.saida}>
          {confirmandoSaida ? (
            <>
              <p>Sair agora apaga o que você respondeu, e nada será gravado. Deseja sair?</p>
              <button type="button" className={estilos.secundario} onClick={() => { setConfirmandoSaida(false); conversa.cancelar() }}>
                Sim, sair e apagar
              </button>
              <button type="button" className={estilos.secundario} onClick={() => setConfirmandoSaida(false)}>
                Continuar o cadastro
              </button>
            </>
          ) : (
            <button type="button" className={estilos.link} onClick={() => setConfirmandoSaida(true)}>
              Cancelar cadastro
            </button>
          )}
        </div>
      )}
    </section>
  )
}
