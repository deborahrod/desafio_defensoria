export type TipoEntrada =
  | 'ESCOLHA'
  | 'LISTA'
  | 'TEXTO'
  | 'RELATO'
  | 'DATA'
  | 'CPF'
  | 'TELEFONE'
  | 'EMAIL'
  | 'CEP'
  | 'VALOR'
  | 'NUMERO'

export type EstadoSessao =
  | 'EM_PREENCHIMENTO'
  | 'AGUARDANDO_ESCLARECIMENTO'
  | 'EM_REVISAO'
  | 'CONFIRMADO'
  | 'CANCELADO'
  | 'EXPIRADO'

export interface Opcao {
  id: string
  rotulo: string
}

export interface ProximaPergunta {
  campo: string
  texto: string
  ajuda: string | null
  tipoEntrada: TipoEntrada
  opcoes: Opcao[]
  tamanhoMaximo: number | null
  /** Dados que a interpretação entendeu, para a pessoa conferir. Só aparece na confirmação. */
  itens?: ItemConfirmacao[]
}

export interface ItemConfirmacao {
  rotulo: string
  valor: string
}

export interface ItemResumo {
  campo: string
  rotulo: string
  valor: string
  situacao: 'INFORMADO' | 'NAO_INFORMADO' | 'OPCIONAL_NAO_PREENCHIDO'
  edicao: ProximaPergunta
}

export interface SecaoResumo {
  titulo: string
  itens: ItemResumo[]
}

export interface Comprovante {
  identificador: string
  confirmadoEm: string
  comPendencias: boolean
}

export interface EstadoConversa {
  estado: EstadoSessao
  modoInteracao: 'ASSISTIDO_POR_IA' | 'ESTRUTURADO'
  versao: number
  proximaPergunta: ProximaPergunta | null
  progresso: { etapa: number; total: number; secao: string; secoes: string[] } | null
  resumo: SecaoResumo[]
  pendencias: string[]
  avisos: string[]
  comprovante: Comprovante | null
  expiraEm: string
  minutosDeInatividade: number
}

/** Resposta de um turno: um valor digitado ou a opção escolhida. */
export interface Entrada {
  valor?: string
  opcao?: string
}
