import { build } from 'vite'
import { expect, test } from 'vitest'

interface Saida {
  output: Array<{ type: 'chunk'; code: string } | { type: 'asset'; source: string | Uint8Array }>
}

/** Gera o pacote de produção em memória e junta todo o texto que o navegador receberia. */
async function textoDoPacote(): Promise<string> {
  const resultado = (await build({ logLevel: 'silent', build: { write: false, minify: false } })) as Saida | Saida[]
  const saidas = Array.isArray(resultado) ? resultado : [resultado]
  return saidas
    .flatMap((s) => s.output)
    .map((item) => (item.type === 'chunk' ? item.code : typeof item.source === 'string' ? item.source : ''))
    .join('\n')
}

test('o pacote entregue ao navegador não contém chave, variáveis nem endereço do provedor de IA', async () => {
  const pacote = await textoDoPacote()

  for (const proibido of ['LLM_API_KEY', 'LLM_BASE_URL', 'LLM_MODEL', 'VITE_', 'api.mistral.ai', 'chat/completions', 'Bearer ']) {
    expect(pacote, proibido).not.toContain(proibido)
  }
  const chaveReal = process.env.LLM_API_KEY
  if (chaveReal) {
    expect(pacote).not.toContain(chaveReal)
  }
}, 60_000)

test('o pacote só fala com a API da própria aplicação', async () => {
  const pacote = await textoDoPacote()

  expect(pacote).toContain('/api/v1/cadastro')
  expect(pacote).not.toMatch(/https?:\/\/(?!www\.w3\.org|react\.dev|reactjs\.org)[a-z0-9.-]+\.[a-z]{2,}\/v\d/i)
}, 60_000)
