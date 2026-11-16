import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect, it } from 'vitest'
import { validarCpf } from './cpf.ts'
import { validarTelefone } from './telefone.ts'

interface Caso {
  entrada: string
  valido: boolean
  mensagemContem?: string
}

// O mesmo arquivo é lido pelo teste do backend (VetoresCompartilhadosTest): interface e servidor concordam.
const vetores: { cpf: Caso[]; telefone: Caso[] } = JSON.parse(
  readFileSync(resolve(__dirname, '../../../testes-compartilhados/validacao.json'), 'utf-8'),
)

function conferir(casos: Caso[], regra: (texto: string) => string | null) {
  expect(casos.length).toBeGreaterThan(5)
  const falhas: string[] = []
  for (const caso of casos) {
    const erro = regra(caso.entrada)
    if (caso.valido) {
      if (erro !== null) falhas.push(`devia aceitar: '${caso.entrada}' (${erro})`)
    } else if (erro === null) {
      falhas.push(`devia recusar: '${caso.entrada}'`)
    } else if (!erro.includes(caso.mensagemContem ?? '')) {
      falhas.push(`mensagem de '${caso.entrada}' não contém '${caso.mensagemContem}': ${erro}`)
    }
  }
  expect(falhas).toEqual([])
}

it('o CPF na interface concorda com os vetores compartilhados com o servidor', () => {
  conferir(vetores.cpf, validarCpf)
})

it('o telefone na interface concorda com os vetores compartilhados com o servidor', () => {
  conferir(vetores.telefone, validarTelefone)
})
