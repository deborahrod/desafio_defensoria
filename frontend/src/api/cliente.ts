const BASE = '/api/v1/cadastro'

export class ErroApi extends Error {
  readonly status: number
  readonly codigo: string
  readonly campo?: string

  constructor(status: number, codigo: string, mensagem: string, campo?: string) {
    super(mensagem)
    this.status = status
    this.codigo = codigo
    this.campo = campo
  }
}

function lerCookie(nome: string): string | undefined {
  const par = document.cookie.split('; ').find((c) => c.startsWith(`${nome}=`))
  return par ? decodeURIComponent(par.slice(nome.length + 1)) : undefined
}

export async function requisitar<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
  const cabecalhos: Record<string, string> = {}
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json'
  if (metodo !== 'GET') {
    const token = lerCookie('XSRF-TOKEN')
    if (token) cabecalhos['X-XSRF-TOKEN'] = token
  }

  let resposta: Response
  try {
    resposta = await fetch(BASE + caminho, {
      method: metodo,
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      credentials: 'same-origin',
    })
  } catch {
    throw new ErroApi(0, 'SEM_CONEXAO', 'Não foi possível falar com o servidor. Verifique a conexão e tente de novo.')
  }

  if (resposta.status === 204) return undefined as T
  const json = await resposta.json().catch(() => null)
  if (!resposta.ok) {
    throw new ErroApi(
      resposta.status,
      json?.codigo ?? 'ERRO',
      json?.mensagem ?? 'Não foi possível concluir a operação. Tente novamente em instantes.',
      json?.campo ?? undefined,
    )
  }
  return json as T
}

/** O servidor entrega o token CSRF em qualquer resposta; sem ele a primeira mutação seria recusada. */
export async function garantirTokenCsrf(): Promise<void> {
  if (lerCookie('XSRF-TOKEN')) return
  await fetch(`${BASE}/atual`, { credentials: 'same-origin' }).catch(() => undefined)
}
