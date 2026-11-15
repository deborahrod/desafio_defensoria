import { useState } from 'react'
import { useConversa } from '../conversa/useConversa.ts'
import estilos from './ChatCadastro.module.css'
import EntradaConversa from './EntradaConversa.tsx'

export default function ChatCadastro() {
  const conversa = useConversa()
  const { estado, transcricao, enviando, falha, encerrada } = conversa
  const [confirmandoSaida, setConfirmandoSaida] = useState(false)

  if (!estado && !encerrada) {
    return (
      <div className={estilos.inicio}>
        <button type="button" className={estilos.comecar} disabled={enviando} onClick={conversa.iniciar}>
          Começar
        </button>
        {falha && <p role="alert" className={estilos.erro}>{falha}</p>}
      </div>
    )
  }

  const pergunta = estado?.proximaPergunta

  return (
    <section className={estilos.chat} aria-label="Conversa do cadastro">
      {estado?.progresso && (
        <p className={estilos.progresso}>
          Etapa {estado.progresso.etapa} de {estado.progresso.total}: {estado.progresso.secao}
        </p>
      )}

      <ol className={estilos.mensagens} role="log" aria-live="polite" aria-label="Mensagens">
        {transcricao.map((m) => (
          <li key={m.id} className={`${estilos.mensagem} ${m.autor === 'pessoa' ? estilos.pessoa : estilos.sistema} ${m.erro ? estilos.recusada : ''}`}>
            <span className={estilos.autor}>{m.autor === 'pessoa' ? 'Você' : 'Defensoria'}:</span> {m.texto}
          </li>
        ))}
      </ol>

      {encerrada ? (
        <div role="alert" className={estilos.aviso}>
          <p>{encerrada}</p>
          <button type="button" className={estilos.comecar} disabled={enviando} onClick={conversa.iniciar}>
            Começar um novo cadastro
          </button>
        </div>
      ) : pergunta ? (
        <EntradaConversa key={`${pergunta.campo}-${estado?.versao}`} pergunta={pergunta} desabilitado={enviando} aoEnviar={conversa.enviar} />
      ) : (
        <p className={estilos.aviso}>Você respondeu todas as perguntas.</p>
      )}

      {enviando && <p className={estilos.status} role="status">Enviando…</p>}
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
