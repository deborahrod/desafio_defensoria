import { useCallback, useRef, useState } from 'react'
import { encerrarSessao, estadoAtual, iniciarSessao, responder } from '../api/cadastro.ts'
import { ErroApi } from '../api/cliente.ts'
import { novoIdentificador } from '../api/identificador.ts'
import type { Entrada, EstadoConversa } from '../api/tipos.ts'

export interface Mensagem {
  id: number
  autor: 'sistema' | 'pessoa'
  texto: string
  erro?: boolean
}

interface Pendente {
  mensagemId: string
  versao: number
  entrada: Entrada
  rotulo: string
}

/** Orquestra a conversa: um envio por vez, nova tentativa com o mesmo mensagemId. */
export function useConversa() {
  const [estado, setEstado] = useState<EstadoConversa | null>(null)
  const [transcricao, setTranscricao] = useState<Mensagem[]>([])
  const [enviando, setEnviando] = useState(false)
  const [falha, setFalha] = useState<string | null>(null)
  const [encerrada, setEncerrada] = useState<string | null>(null)
  const emVoo = useRef(false)
  const pendente = useRef<Pendente | null>(null)
  const proximoId = useRef(1)

  const acrescentar = useCallback((novas: Omit<Mensagem, 'id'>[]) => {
    setTranscricao((atual) => [...atual, ...novas.map((m) => ({ ...m, id: proximoId.current++ }))])
  }, [])

  const receber = useCallback(
    (novo: EstadoConversa, antes: Omit<Mensagem, 'id'>[] = []) => {
      const depois = [
        ...novo.avisos.map((texto) => ({ autor: 'sistema' as const, texto })),
        ...(novo.proximaPergunta ? [{ autor: 'sistema' as const, texto: novo.proximaPergunta.texto }] : []),
      ]
      acrescentar([...antes, ...depois])
      setEstado(novo)
    },
    [acrescentar],
  )

  const protegido = useCallback(async (acao: () => Promise<void>) => {
    if (emVoo.current) return
    emVoo.current = true
    setEnviando(true)
    setFalha(null)
    try {
      await acao()
    } finally {
      emVoo.current = false
      setEnviando(false)
    }
  }, [])

  const tratarErro = useCallback(
    async (erro: unknown) => {
      if (!(erro instanceof ErroApi)) throw erro
      if (erro.status === 400) {
        pendente.current = null
        acrescentar([{ autor: 'sistema', texto: erro.message, erro: true }])
      } else if (erro.status === 409) {
        pendente.current = null
        const atual = await estadoAtual().catch(() => null)
        if (atual) setEstado(atual)
        acrescentar([{ autor: 'sistema', texto: 'O cadastro foi atualizado. Responda de novo à pergunta atual.', erro: true }])
      } else if (erro.status === 401 || erro.status === 410) {
        pendente.current = null
        setEncerrada(erro.message)
      } else {
        // Sem conexão, 429 ou 5xx: o rascunho está preservado e a pessoa pode tentar de novo.
        setFalha(erro.message)
      }
    },
    [acrescentar],
  )

  const iniciar = useCallback(
    () =>
      protegido(async () => {
        try {
          setEncerrada(null)
          setTranscricao([])
          receber(await iniciarSessao())
        } catch (erro) {
          await tratarErro(erro)
        }
      }),
    [protegido, receber, tratarErro],
  )

  const executar = useCallback(
    (item: Pendente) =>
      protegido(async () => {
        try {
          const novo = await responder(item.mensagemId, item.versao, item.entrada)
          pendente.current = null
          receber(novo, [{ autor: 'pessoa', texto: item.rotulo }])
        } catch (erro) {
          await tratarErro(erro)
        }
      }),
    [protegido, receber, tratarErro],
  )

  const enviar = useCallback(
    (entrada: Entrada, rotulo: string) => {
      if (!estado || emVoo.current) return Promise.resolve()
      const item = { mensagemId: novoIdentificador(), versao: estado.versao, entrada, rotulo }
      pendente.current = item
      return executar(item)
    },
    [estado, executar],
  )

  const tentarNovamente = useCallback(() => {
    return pendente.current ? executar(pendente.current) : Promise.resolve()
  }, [executar])

  const cancelar = useCallback(
    () =>
      protegido(async () => {
        await encerrarSessao().catch(() => undefined)
        pendente.current = null
        setEstado(null)
        setTranscricao([])
        setEncerrada(null)
      }),
    [protegido],
  )

  return { estado, transcricao, enviando, falha, encerrada, iniciar, enviar, tentarNovamente, cancelar }
}
