import { describe, expect, it } from 'vitest'
import { mascararCpf, validarCpf } from './cpf.ts'
import { dataDeHoje } from './data.ts'
import { mascararTelefone, validarTelefone } from './telefone.ts'

describe('CPF', () => {
  it('aplica a máscara enquanto digita e ignora o que não é número', () => {
    expect(mascararCpf('5')).toBe('5')
    expect(mascararCpf('5299')).toBe('529.9')
    expect(mascararCpf('529982')).toBe('529.982')
    expect(mascararCpf('5299822')).toBe('529.982.2')
    expect(mascararCpf('52998224725')).toBe('529.982.247-25')
    expect(mascararCpf('529.982.247-25999')).toBe('529.982.247-25')
    expect(mascararCpf('abc')).toBe('')
  })

  it.each(['529.982.247-25', '52998224725', '111.444.777-35', '123.456.789-09'])('aceita %s', (cpf) => {
    expect(validarCpf(cpf)).toBeNull()
  })

  it('diz quantos números foram informados quando a quantidade está errada', () => {
    expect(validarCpf('529.982.247-2')).toContain('você informou 10')
    expect(validarCpf('')).toContain('você informou 0')
  })

  it.each(['000.000.000-00', '111.111.111-11', '999.999.999-99'])('recusa sequência repetida %s', (cpf) => {
    expect(validarCpf(cpf)).toContain('números são iguais')
  })

  it.each(['529.982.247-26', '123.456.789-10', '111.444.777-36'])('recusa dígito verificador errado em %s', (cpf) => {
    expect(validarCpf(cpf)).toContain('dígitos verificadores não conferem')
  })
})

describe('telefone', () => {
  it('aplica a máscara de celular e de fixo enquanto digita', () => {
    expect(mascararTelefone('6')).toBe('(6')
    expect(mascararTelefone('62')).toBe('(62')
    expect(mascararTelefone('629')).toBe('(62) 9')
    expect(mascararTelefone('6291234')).toBe('(62) 9123-4')
    expect(mascararTelefone('6232010000')).toBe('(62) 3201-0000')
    expect(mascararTelefone('62912345678')).toBe('(62) 91234-5678')
    expect(mascararTelefone('(62) 91234-5678999')).toBe('(62) 91234-5678')
    expect(mascararTelefone('')).toBe('')
  })

  it('mantém número internacional como digitado', () => {
    expect(mascararTelefone('+351 912 345 678')).toBe('+351 912 345 678')
    expect(validarTelefone('+351 912 345 678')).toBeNull()
    expect(validarTelefone('+12')).toContain('código do país')
    expect(validarTelefone('+55 (62) 91234-5678')).toBeNull()
    expect(validarTelefone('+55 (02) 91234-5678')).toContain('DDD 02')
  })

  it.each(['(62) 91234-5678', '(11) 99876-5432', '(62) 3201-0000', '(11) 4002-8922', '(62) 99999-9998'])('aceita %s', (tel) => {
    expect(validarTelefone(tel)).toBeNull()
  })

  it('recusa DDD inexistente', () => {
    expect(validarTelefone('(02) 91234-5678')).toContain('O DDD 02 não existe')
    expect(validarTelefone('(20) 91234-5678')).toContain('O DDD 20 não existe')
  })

  it('recusa quantidade errada de números', () => {
    for (const tel of ['1234', '(62) 9123-456', '(62) 91234-56789', '']) {
      expect(validarTelefone(tel)).toContain('10 números para fixo ou 11 para celular')
    }
  })

  it('exige 9 no celular e 2 a 5 no fixo', () => {
    expect(validarTelefone('(62) 81234-5678')).toContain('Celular tem 11 números')
    expect(validarTelefone('(62) 9123-4567')).toContain('Telefone fixo tem 10 números')
    expect(validarTelefone('(62) 6201-0000')).toContain('Telefone fixo tem 10 números')
  })

  it('recusa números com todos os dígitos iguais', () => {
    expect(validarTelefone('(62) 99999-9999')).toContain('todos os dígitos são iguais')
    expect(validarTelefone('(62) 3333-3333')).toContain('todos os dígitos são iguais')
  })
})

describe('data de hoje', () => {
  it('usa o fuso de Brasília: à noite, o dia ainda é o anterior ao do UTC', () => {
    expect(dataDeHoje(new Date('2026-03-11T02:30:00Z'))).toBe('2026-03-10')
    expect(dataDeHoje(new Date('2026-03-11T03:00:00Z'))).toBe('2026-03-11')
  })

  it('devolve aaaa-mm-dd com zeros à esquerda', () => {
    expect(dataDeHoje(new Date('2026-01-05T15:00:00Z'))).toBe('2026-01-05')
  })
})
