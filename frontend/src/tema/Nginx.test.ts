import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, test } from 'vitest'

const conf = readFileSync(resolve(__dirname, '../../nginx.conf'), 'utf-8')

test('o nginx envia os cabeçalhos de segurança em todas as respostas', () => {
  for (const cabecalho of [
    'Content-Security-Policy',
    'X-Content-Type-Options',
    'X-Frame-Options',
    'Referrer-Policy',
    'Permissions-Policy',
    'Cross-Origin-Opener-Policy',
  ]) {
    expect(conf, cabecalho).toMatch(new RegExp(`add_header ${cabecalho} ".+" always;`))
  }
})

test('os cabeçalhos ficam no nível do servidor, porque um add_header em location descartaria os demais', () => {
  const dentroDeLocation = conf.split(/location\s+[^{]+\{/).slice(1).join('\n')

  expect(dentroDeLocation).not.toMatch(/add_header/)
})

test('a política de conteúdo só aceita a própria origem e proíbe script inline, eval e enquadramento', () => {
  const csp = /add_header Content-Security-Policy "([^"]+)"/.exec(conf)![1]

  expect(csp).toContain("default-src 'none'")
  expect(csp).toContain("script-src 'self'")
  expect(csp).toContain("frame-ancestors 'none'")
  expect(csp).toContain("object-src 'none'")
  const script = /script-src ([^;]+)/.exec(csp)![1]
  expect(script).not.toMatch(/unsafe-inline|unsafe-eval|https?:|\*/)
  expect(csp).not.toMatch(/https?:\/\//)
})

test('o endereço encaminhado à API é o da conexão e o cabeçalho do cliente é descartado', () => {
  expect(conf).toMatch(/proxy_set_header X-Forwarded-For \$remote_addr;/)
  expect(conf).not.toContain('proxy_add_x_forwarded_for')
})

test('o nginx não anuncia a versão', () => {
  expect(conf).toMatch(/server_tokens off;/)
})
