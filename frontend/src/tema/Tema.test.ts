import { readFileSync } from 'node:fs'
import { expect, test } from 'vitest'

const ler = (caminho: string) => readFileSync(new URL(caminho, import.meta.url), 'utf-8')

test('os tokens usam as cores e a fonte observadas no portal da Defensoria', () => {
  const tokens = ler('./tokens.css').toLowerCase()

  for (const cor of ['#007b2f', '#95ae23', '#ffffff', '#565656']) {
    expect(tokens).toContain(cor)
  }
  expect(tokens).toMatch(/--fonte-base:\s*'roboto'/)
})

test('a marca é o arquivo do portal, com as cores institucionais', () => {
  const marca = ler('../../public/dpe-logo.svg').toLowerCase()

  expect(marca).toContain('<svg')
  expect(marca).toContain('#007b2f')
  expect(marca).toContain('#95ae23')
})

test('os componentes usam tokens do tema e não cores soltas', () => {
  const arquivos = [
    '../componentes/CabecalhoInstitucional.module.css',
    '../componentes/RodapeInstitucional.module.css',
    '../componentes/ChatCadastro.module.css',
    '../componentes/EntradaConversa.module.css',
    '../componentes/PainelRespostas.module.css',
    '../componentes/RevisaoFinal.module.css',
    '../componentes/PaginaInicial.module.css',
    '../componentes/EtapasProgresso.module.css',
    '../componentes/Cadastro.module.css',
    '../componentes/ComprovanteCadastro.module.css',
  ]

  for (const arquivo of arquivos) {
    expect(ler(arquivo), arquivo).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
  }
})
