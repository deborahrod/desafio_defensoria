import { useCallback, useRef, useState } from 'react'
import { confirmarCadastro, corrigirCampo, encerrarSessao, estadoAtual, iniciarSessao, responder } from '../api/cadastro.ts'
import { ErroApi } from '../api/cliente.ts'
import { novoIdentificador } from '../api/identificador.ts'
import type { Entrada, EstadoConversa, ItemResumo } from '../api/tipos.ts'

export interface Mensagem {
  id: number
  autor: 'sistema' | 'pessoa'
  texto: string
  erro?: boolean
}

/** Orquestra a conversa: uma operação por vez, nova tentativa idempotente após falha de rede. */
export function useConversa() {
  const [estado, setEstado] = useState<EstadoConversa | null>(null)
  const [transcricao, setTranscricao] = useState<Mensagem[]>([])
  const [enviando, setEnviando] = useState(false)
  const [falha, setFalha] = useState<string | null>(null)
  const [recusa, setRecusa] = useState<string | null>(null)
  const [encerrada, setEncerrada] = useState<string | null>(null)
  const emVoo = useRef(false)
  const tentativa = useRef<(() => Promise<void>) | null>(null)
  const chaveConfirmacao = useRef<{ versao: number; chave: string } | null>(null)
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

  const tratarErro = useCallback(
    async (erro: unknown, emRevisao: boolean) => {
      if (!(erro instanceof ErroApi)) throw erro
      if (erro.status === 401 || erro.status === 410) {
        tentativa.current = null
        setEncerrada(erro.message)
      } else if (erro.codigo === 'VERSAO_DESATUALIZADA') {
        tentativa.current = null
        const atual = await estadoAtual().catch(() => null)
        if (atual) setEstado(atual)
        setRecusa('O cadastro foi atualizado em outra tela. Confira os dados e tente de novo.')
      } else if (erro.status === 400 || erro.status === 409) {
        tentativa.current = null
        if (emRevisao || erro.status === 409) setRecusa(erro.message)
        else acrescentar([{ autor: 'sistema', texto: erro.message, erro: true }])
      } else {
        // Sem conexão, 429 ou 5xx: o rascunho está preservado e a pessoa pode tentar de novo.
        setFalha(erro.message)
      }
    },
    [acrescentar],
  )

  const executar = useCallback(
    async (acao: () => Promise<void>, emRevisao = false) => {
      if (emVoo.current) return
      emVoo.current = true
      setEnviando(true)
      setFalha(null)
      setRecusa(null)
      try {
        await acao()
      } catch (erro) {
        await tratarErro(erro, emRevisao)
      } finally {
        emVoo.current = false
        setEnviando(false)
      }
    },
    [tratarErro],
  )

  const iniciar = useCallback(
    () =>
      executar(async () => {
        setEncerrada(null)
        setTranscricao([])
        receber(await iniciarSessao())
      }),
    [executar, receber],
  )

  const enviar = useCallback(
    (entrada: Entrada, rotulo: string) => {
      if (!estado || emVoo.current) return Promise.resolve()
      const mensagemId = novoIdentificador()
      const versao = estado.versao
      // Nova tentativa reaproveita o mesmo mensagemId, então o servidor não processa duas vezes.
      const acao = async () => {
        const novo = await responder(mensagemId, versao, entrada)
        tentativa.current = null
        receber(novo, [{ autor: 'pessoa', texto: rotulo }])
      }
      tentativa.current = () => executar(acao)
      return executar(acao)
    },
    [estado, executar, receber],
  )

  const corrigir = useCallback(
    (item: ItemResumo, entrada: Entrada, rotulo: string) => {
      if (!estado || emVoo.current) return Promise.resolve()
      const versao = estado.versao
      const acao = async () => {
        const novo = await corrigirCampo(item.campo, versao, entrada)
        tentativa.current = null
        receber(novo, [{ autor: 'pessoa', texto: `${item.rotulo}: ${rotulo}` }])
      }
      tentativa.current = () => executar(acao, true)
      return executar(acao, true)
    },
    [estado, executar, receber],
  )

  const confirmar = useCallback(() => {
    if (!estado || emVoo.current) return Promise.resolve()
    const versao = estado.versao
    // A chave vale para a versão revisada: repetir o pedido nunca grava duas vezes.
    if (chaveConfirmacao.current?.versao !== versao) {
      chaveConfirmacao.current = { versao, chave: novoIdentificador() }
    }
    const { chave } = chaveConfirmacao.current
    const acao = async () => {
      const novo = await confirmarCadastro(versao, chave)
      tentativa.current = null
      setEstado(novo)
    }
    tentativa.current = () => executar(acao, true)
    return executar(acao, true)
  }, [estado, executar])

  const tentarNovamente = useCallback(() => tentativa.current?.() ?? Promise.resolve(), [])

  const cancelar = useCallback(
    () =>
      executar(async () => {
        await encerrarSessao().catch(() => undefined)
        tentativa.current = null
        chaveConfirmacao.current = null
        setEstado(null)
        setTranscricao([])
        setEncerrada(null)
      }),
    [executar],
  )

  return { estado, transcricao, enviando, falha, recusa, encerrada, iniciar, enviar, corrigir, confirmar, tentarNovamente, cancelar }
}
