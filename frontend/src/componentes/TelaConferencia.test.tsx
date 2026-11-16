import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { expect, test } from 'vitest'
import { fetchSimulado, json, prepararFetch, revisao } from '../testes/fixtures.ts'
import Cadastro from './Cadastro.tsx'

prepararFetch()

async function abrir() {
  fetchSimulado.mockResolvedValueOnce(json(revisao(8), 201))
  const usuario = userEvent.setup()
  const tela = render(<Cadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  await screen.findByRole('heading', { name: 'Revise seus dados' })
  return { usuario, ...tela }
}

test('a conferência ocupa a tela toda, com um cartão por seção e foco no título', async () => {
  await abrir()

  expect(screen.getByRole('heading', { name: 'Revise seus dados' })).toHaveFocus()
  expect(screen.getByRole('region', { name: 'Identificação' })).toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Contato' })).toBeInTheDocument()
  expect(screen.queryByLabelText('Sua resposta')).not.toBeInTheDocument()
  expect(screen.queryByRole('log')).not.toBeInTheDocument()
})

test('ausências declaradas ganham selo com texto, além do destaque visual', async () => {
  await abrir()

  const identificacao = within(screen.getByRole('region', { name: 'Identificação' }))
  expect(identificacao.getAllByText('Ausência declarada').length).toBeGreaterThanOrEqual(1)
  const contato = within(screen.getByRole('region', { name: 'Contato' }))
  expect(contato.queryByText('Ausência declarada')).not.toBeInTheDocument()
})

test('cancelar pede confirmação, e continuar a revisão não chama o servidor', async () => {
  const { usuario } = await abrir()

  await usuario.click(screen.getByRole('button', { name: 'Cancelar cadastro' }))
  expect(screen.getByText(/Sair agora apaga o que você respondeu/)).toBeInTheDocument()
  await usuario.click(screen.getByRole('button', { name: 'Continuar a revisão' }))

  expect(screen.queryByText(/Sair agora apaga/)).not.toBeInTheDocument()
  expect(fetchSimulado).toHaveBeenCalledTimes(1)
})

test('confirmar a saída encerra a sessão e volta ao início', async () => {
  const { usuario } = await abrir()
  fetchSimulado.mockResolvedValueOnce(new Response(null, { status: 204 }))

  await usuario.click(screen.getByRole('button', { name: 'Cancelar cadastro' }))
  await usuario.click(screen.getByRole('button', { name: 'Sim, sair e apagar' }))

  await screen.findByRole('button', { name: 'Começar' })
  await waitFor(() => expect(fetchSimulado.mock.calls[1][1]?.method).toBe('DELETE'))
})

test('a tela de conferência não tem violações de acessibilidade', async () => {
  const { container, usuario } = await abrir()
  await usuario.click(screen.getByRole('button', { name: 'Corrigir Telefone' }))

  const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  expect(resultado.violations).toEqual([])
})
