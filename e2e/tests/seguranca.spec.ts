import { expect, test } from '@playwright/test'

// Teto de requisições por origem configurado no Compose que está no ar (padrão 120 por minuto).
const LIMITE_POR_ORIGEM = Number(process.env.E2E_LIMITE_ORIGEM ?? 120)

test('o nginx envia os cabeçalhos de segurança e não anuncia a versão', async ({ request }) => {
  const resposta = await request.get('/')
  const cabecalhos = resposta.headers()

  expect(resposta.status()).toBe(200)
  expect(cabecalhos['content-security-policy']).toContain("default-src 'none'")
  expect(cabecalhos['content-security-policy']).toContain("frame-ancestors 'none'")
  expect(cabecalhos['content-security-policy']).not.toContain('unsafe-eval')
  expect(cabecalhos['x-content-type-options']).toBe('nosniff')
  expect(cabecalhos['x-frame-options']).toBe('DENY')
  expect(cabecalhos['referrer-policy']).toBe('no-referrer')
  expect(cabecalhos['permissions-policy']).toContain('camera=()')
  expect(cabecalhos['server']).toBe('nginx')
})

test('a API não responde sem sessão, sem cache e sem cabeçalhos duplicados pelo proxy', async ({ request }) => {
  const resposta = await request.get('/api/v1/cadastro/atual')

  expect(resposta.status()).toBe(401)
  expect(resposta.headers()['cache-control']).toContain('no-store')
  const brutos = resposta.headersArray().filter((h) => h.name.toLowerCase() === 'x-content-type-options')
  expect(brutos).toHaveLength(1)
})

test('mutação sem token CSRF é recusada pelo proxy e pela API', async ({ request }) => {
  const resposta = await request.post('/api/v1/cadastro/sessao')

  expect(resposta.status()).toBe(403)
})

// Regressão do X-Forwarded-For: o nginx anexava o cabeçalho do cliente e a API usava a primeira entrada,
// então um valor diferente a cada requisição dava um balde novo e o teto nunca era atingido.
test('forjar X-Forwarded-For não escapa do teto por origem', async ({ request }) => {
  const total = LIMITE_POR_ORIGEM + 40
  let limitadas = 0
  let corpo: { codigo?: string } = {}
  let esperaInformada = false

  for (let i = 0; i < total; i++) {
    const resposta = await request.get('/api/v1/cadastro/atual', {
      headers: { 'X-Forwarded-For': `10.${i % 250}.${Math.floor(i / 250)}.${(i * 7) % 250}` },
    })
    if (resposta.status() === 429) {
      limitadas++
      corpo = await resposta.json()
      esperaInformada = Number(resposta.headers()['retry-after']) > 0
    }
  }

  expect(limitadas, 'com o cabeçalho forjado nenhuma requisição era limitada').toBeGreaterThanOrEqual(30)
  expect(corpo.codigo).toBe('LIMITE_EXCEDIDO')
  expect(esperaInformada).toBe(true)
})
