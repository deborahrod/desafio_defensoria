import { afterEach, beforeEach, vi } from 'vitest'
import type { Comprovante, EstadoConversa, ItemResumo, ProximaPergunta } from '../api/tipos.ts'

export const PARA_QUEM: ProximaPergunta = {
  campo: 'paraQuem',
  texto: 'Este cadastro é para você ou para outra pessoa?',
  ajuda: null,
  tipoEntrada: 'ESCOLHA',
  opcoes: [
    { id: 'PROPRIA_PESSOA', rotulo: 'Para mim' },
    { id: 'OUTRA_PESSOA', rotulo: 'Para outra pessoa' },
  ],
  tamanhoMaximo: null,
}

export const NOME: ProximaPergunta = {
  campo: 'nome',
  texto: 'Como você se chama?',
  ajuda: null,
  tipoEntrada: 'TEXTO',
  opcoes: [],
  tamanhoMaximo: 200,
}

export const TELEFONE: ItemResumo = {
  campo: 'telefone',
  rotulo: 'Telefone',
  valor: '(62) 91234-5678',
  situacao: 'INFORMADO',
  edicao: { campo: 'telefone', texto: 'Qual número podemos usar?', ajuda: null, tipoEntrada: 'TELEFONE', opcoes: [], tamanhoMaximo: 20 },
}

export const CPF: ItemResumo = {
  campo: 'situacaoCpf',
  rotulo: 'Tem CPF para informar',
  valor: 'Não possuo CPF',
  situacao: 'INFORMADO',
  edicao: {
    campo: 'situacaoCpf',
    texto: 'Você possui o CPF para informar agora?',
    ajuda: null,
    tipoEntrada: 'ESCOLHA',
    opcoes: [
      { id: 'INFORMADO', rotulo: 'Sim, vou informar' },
      { id: 'NAO_POSSUI', rotulo: 'Não possuo CPF' },
    ],
    tamanhoMaximo: null,
  },
}

export const NASCIMENTO: ItemResumo = {
  campo: 'nascimento',
  rotulo: 'Data de nascimento',
  valor: 'Não soube informar',
  situacao: 'NAO_INFORMADO',
  edicao: { campo: 'nascimento', texto: 'Qual é a sua data de nascimento?', ajuda: null, tipoEntrada: 'DATA', opcoes: [], tamanhoMaximo: 10 },
}

const COMUM: Omit<EstadoConversa, 'estado' | 'versao'> = {
  modoInteracao: 'ESTRUTURADO',
  proximaPergunta: null,
  progresso: { etapa: 1, total: 5, secao: 'Quem preenche' },
  resumo: [],
  pendencias: [],
  avisos: [],
  comprovante: null,
  expiraEm: '2026-11-15T12:30:00Z',
  minutosDeInatividade: 30,
}

/** Estado em preenchimento, ou em revisão quando não há próxima pergunta. */
export function estado(versao: number, pergunta: ProximaPergunta | null, avisos: string[] = []): EstadoConversa {
  return { ...COMUM, estado: pergunta ? 'EM_PREENCHIMENTO' : 'EM_REVISAO', versao, proximaPergunta: pergunta, avisos }
}

export function revisao(versao: number, sobrescrever: Partial<EstadoConversa> = {}): EstadoConversa {
  return {
    ...COMUM,
    estado: 'EM_REVISAO',
    versao,
    progresso: { etapa: 5, total: 5, secao: 'Revisão' },
    resumo: [
      { titulo: 'Identificação', itens: [NASCIMENTO, CPF] },
      { titulo: 'Contato', itens: [TELEFONE] },
    ],
    pendencias: ['Data de nascimento: Não soube informar', 'CPF: Não possuo CPF'],
    ...sobrescrever,
  }
}

export function confirmado(versao: number, comprovante: Partial<Comprovante> = {}): EstadoConversa {
  return {
    ...revisao(versao),
    estado: 'CONFIRMADO',
    resumo: [],
    pendencias: [],
    comprovante: { identificador: '0b8f1c0e-5f6a-4c53-9a7e-2f3d1f3c9a11', confirmadoEm: '2026-11-15T15:00:00Z', comPendencias: false, ...comprovante },
  }
}

export function json(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } })
}

export const fetchSimulado = vi.fn<typeof fetch>()

/** Corpo JSON enviado na n-ésima chamada ao fetch simulado. */
export function corpoEnviado(chamada: number) {
  return JSON.parse(String(fetchSimulado.mock.calls[chamada][1]?.body))
}

/** Troca o fetch por um simulado e entrega o token CSRF, como faz o servidor. */
export function prepararFetch() {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-de-teste'
    vi.stubGlobal('fetch', fetchSimulado)
  })

  afterEach(() => {
    fetchSimulado.mockReset()
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })
}
