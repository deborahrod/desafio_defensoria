import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { afterEach, expect, test, vi } from 'vitest'
import type { ProximaPergunta } from '../api/tipos.ts'
import EntradaConversa from './EntradaConversa.tsx'

const CPF: ProximaPergunta = {
  campo: 'cpf',
  texto: 'Qual é o seu CPF?',
  ajuda: null,
  tipoEntrada: 'CPF',
  opcoes: [{ id: 'SEM_CPF', rotulo: 'Não tenho CPF' }],
  tamanhoMaximo: null,
}

const TELEFONE: ProximaPergunta = {
  campo: 'telefone',
  texto: 'Qual número podemos usar?',
  ajuda: null,
  tipoEntrada: 'TELEFONE',
  opcoes: [{ id: 'SEM_TELEFONE', rotulo: 'Não tenho telefone' }],
  tamanhoMaximo: 20,
}

function abrir(pergunta: ProximaPergunta) {
  const aoEnviar = vi.fn()
  const resultado = render(<EntradaConversa pergunta={pergunta} desabilitado={false} aoEnviar={aoEnviar} />)
  return { aoEnviar, usuario: userEvent.setup(), ...resultado }
}

test('CPF ganha máscara enquanto a pessoa digita', async () => {
  const { usuario } = abrir(CPF)
  const campo = screen.getByLabelText('Sua resposta')

  await usuario.type(campo, '52998224725')

  expect(campo).toHaveValue('529.982.247-25')
})

test('CPF inválido não é enviado e mostra o motivo, que some ao corrigir', async () => {
  const { usuario, aoEnviar } = abrir(CPF)
  const campo = screen.getByLabelText('Sua resposta')

  await usuario.type(campo, '11111111111{Enter}')

  expect(await screen.findByRole('alert')).toHaveTextContent('os 11 números são iguais')
  expect(campo).toHaveAttribute('aria-invalid', 'true')
  expect(campo).toHaveAccessibleDescription(/os 11 números são iguais/)
  expect(aoEnviar).not.toHaveBeenCalled()

  await usuario.type(campo, '{Backspace}')
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  expect(campo).not.toHaveAttribute('aria-invalid')
})

test('CPF com dígito verificador errado não é enviado', async () => {
  const { usuario, aoEnviar } = abrir(CPF)

  await usuario.type(screen.getByLabelText('Sua resposta'), '52998224726{Enter}')

  expect(await screen.findByRole('alert')).toHaveTextContent('dígitos verificadores não conferem')
  expect(aoEnviar).not.toHaveBeenCalled()
})

test('CPF válido é enviado com a máscara e o campo é limpo', async () => {
  const { usuario, aoEnviar } = abrir(CPF)

  await usuario.type(screen.getByLabelText('Sua resposta'), '52998224725{Enter}')

  expect(aoEnviar).toHaveBeenCalledWith({ valor: '529.982.247-25' }, '529.982.247-25')
  expect(screen.getByLabelText('Sua resposta')).toHaveValue('')
})

test('apagar com backspace atravessa os símbolos da máscara', async () => {
  const { usuario } = abrir(TELEFONE)
  const campo = screen.getByLabelText('Sua resposta')

  await usuario.type(campo, '6232')
  expect(campo).toHaveValue('(62) 32')
  await usuario.type(campo, '{Backspace}{Backspace}{Backspace}')

  expect(campo).toHaveValue('(6')
})

test('telefone ganha máscara de celular e fixo', async () => {
  const { usuario } = abrir(TELEFONE)
  const campo = screen.getByLabelText('Sua resposta')

  await usuario.type(campo, '62912345678')
  expect(campo).toHaveValue('(62) 91234-5678')

  await usuario.clear(campo)
  await usuario.type(campo, '6232010000')
  expect(campo).toHaveValue('(62) 3201-0000')
})

test('telefone com DDD inexistente ou celular sem 9 não é enviado', async () => {
  const { usuario, aoEnviar } = abrir(TELEFONE)
  const campo = screen.getByLabelText('Sua resposta')

  await usuario.type(campo, '02912345678{Enter}')
  expect(await screen.findByRole('alert')).toHaveTextContent('O DDD 02 não existe')

  await usuario.clear(campo)
  await usuario.type(campo, '62812345678{Enter}')
  expect(await screen.findByRole('alert')).toHaveTextContent('Celular tem 11 números')
  expect(aoEnviar).not.toHaveBeenCalled()
})

test('telefone internacional é aceito sem máscara nacional', async () => {
  const { usuario, aoEnviar } = abrir(TELEFONE)

  await usuario.type(screen.getByLabelText('Sua resposta'), '+351 912 345 678{Enter}')

  expect(aoEnviar).toHaveBeenCalledWith({ valor: '+351 912 345 678' }, '+351 912 345 678')
})

test('não tenho telefone envia a ausência sem validar o campo', async () => {
  const { usuario, aoEnviar } = abrir(TELEFONE)

  await usuario.click(screen.getByRole('button', { name: 'Não tenho telefone' }))

  expect(aoEnviar).toHaveBeenCalledWith({ opcao: 'SEM_TELEFONE' }, 'Não tenho telefone')
})

test('campo com erro de validação não tem violações de acessibilidade', async () => {
  const { usuario, container } = abrir(CPF)
  await usuario.type(screen.getByLabelText('Sua resposta'), '11111111111{Enter}')
  await screen.findByRole('alert')

  const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  expect(resultado.violations).toEqual([])
})

const NASCIMENTO: ProximaPergunta = {
  campo: 'nascimento',
  texto: 'Qual é a sua data de nascimento?',
  ajuda: null,
  tipoEntrada: 'DATA',
  opcoes: [],
  tamanhoMaximo: 10,
}

afterEach(() => {
  vi.useRealTimers()
})

test('o limite da data de nascimento é o dia de hoje, no fuso de Brasília, e acompanha a virada do dia', () => {
  vi.useFakeTimers({ toFake: ['Date'] })

  vi.setSystemTime(new Date('2026-03-11T02:30:00Z'))
  const { unmount } = abrir(NASCIMENTO)
  expect(screen.getByLabelText('Sua resposta')).toHaveAttribute('max', '2026-03-10')
  unmount()

  vi.setSystemTime(new Date('2026-03-11T03:30:00Z'))
  abrir(NASCIMENTO)
  expect(screen.getByLabelText('Sua resposta')).toHaveAttribute('max', '2026-03-11')
})
