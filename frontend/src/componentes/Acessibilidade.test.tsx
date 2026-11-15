import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import axe from 'axe-core'
import { expect, test } from 'vitest'
import { NOME, PARA_QUEM, confirmado, estado, fetchSimulado, json, prepararFetch, revisao } from '../testes/fixtures.ts'
import ChatCadastro from './ChatCadastro.tsx'

prepararFetch()

/** Regras do axe, exceto contraste: o jsdom não calcula estilos, e o contraste é conferido no navegador. */
async function violacoes(container: HTMLElement) {
  const resultado = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } })
  return resultado.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.html).join(' | ')}`)
}

async function comecarCom(inicial: ReturnType<typeof estado>) {
  fetchSimulado.mockResolvedValueOnce(json(inicial, 201))
  const usuario = userEvent.setup()
  const tela = render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  return { usuario, tela }
}

test('tela inicial não tem violações de acessibilidade', async () => {
  const { container } = render(<ChatCadastro />)

  expect(await violacoes(container)).toEqual([])
})

test('pergunta de escolha não tem violações de acessibilidade', async () => {
  const { tela } = await comecarCom(estado(0, PARA_QUEM, ['Vou fazer algumas perguntas.']))
  await screen.findByText(PARA_QUEM.texto)

  expect(await violacoes(tela.container)).toEqual([])
})

test('pergunta de texto com ajuda e mensagem de erro não tem violações', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, { ...NOME, ajuda: 'Pode ser o nome completo ou só o primeiro.' }), 201))
  fetchSimulado.mockResolvedValueOnce(json({ codigo: 'RESPOSTA_INVALIDA', mensagem: 'Preciso que você informe o nome para continuar.' }, 400))
  const usuario = userEvent.setup()
  const { container } = render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  await usuario.type(await screen.findByLabelText('Sua resposta'), '123{Enter}')
  await screen.findByText(/Preciso que você informe o nome/)

  expect(screen.getByLabelText('Sua resposta')).toHaveAccessibleDescription('Pode ser o nome completo ou só o primeiro.')
  expect(await violacoes(container)).toEqual([])
})

test('revisão com correção aberta não tem violações de acessibilidade', async () => {
  const { usuario, tela } = await comecarCom(revisao(8))
  await screen.findByRole('heading', { name: 'Revise seus dados' })
  await usuario.click(screen.getByRole('button', { name: 'Corrigir Telefone' }))

  expect(await violacoes(tela.container)).toEqual([])
})

test('comprovante não tem violações de acessibilidade', async () => {
  const { usuario, tela } = await comecarCom(revisao(8))
  fetchSimulado.mockResolvedValueOnce(json(confirmado(8, { comPendencias: true })))
  await usuario.click(await screen.findByRole('button', { name: 'Confirmar cadastro' }))
  await screen.findByRole('heading', { name: 'Cadastro gravado' })

  expect(await violacoes(tela.container)).toEqual([])
})

test('toda a jornada, da abertura ao comprovante, funciona só com o teclado', async () => {
  const unico = { ...revisao(2), resumo: [revisao(2).resumo[1]], pendencias: [] }
  fetchSimulado
    .mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))
    .mockResolvedValueOnce(json(estado(1, NOME)))
    .mockResolvedValueOnce(json(unico))
    .mockResolvedValueOnce(json({ ...unico, versao: 3 }))
    .mockResolvedValueOnce(json(confirmado(3)))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)

  await usuario.tab()
  expect(screen.getByRole('button', { name: 'Começar' })).toHaveFocus()
  await usuario.keyboard('{Enter}')

  // Pergunta de escolha: o foco já está na primeira opção.
  await waitFor(() => expect(screen.getByRole('button', { name: 'Para mim' })).toHaveFocus())
  await usuario.keyboard('{Enter}')

  // Pergunta de texto: o foco vai para o campo, e Enter envia.
  await waitFor(() => expect(screen.getByLabelText('Sua resposta')).toHaveFocus())
  await usuario.keyboard('Ana Fictícia{Enter}')

  // Revisão: o foco vai ao título e a ordem do Tab segue a ordem visual.
  const titulo = await screen.findByRole('heading', { name: 'Revise seus dados' })
  await waitFor(() => expect(titulo).toHaveFocus())
  await usuario.tab()
  expect(screen.getByRole('button', { name: 'Corrigir Telefone' })).toHaveFocus()
  await usuario.keyboard('{Enter}')
  await waitFor(() => expect(screen.getByLabelText('Sua resposta')).toHaveFocus())
  await usuario.keyboard('(62) 99999-0000{Enter}')

  // Depois da correção o foco volta ao título, e o botão de confirmar é alcançável pelo Tab.
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Revise seus dados' })).toHaveFocus())
  await usuario.tab()
  await usuario.tab()
  expect(screen.getByRole('button', { name: 'Confirmar cadastro' })).toHaveFocus()
  await usuario.keyboard('{Enter}')

  const comprovante = await screen.findByRole('heading', { name: 'Cadastro gravado' })
  await waitFor(() => expect(comprovante).toHaveFocus())
  expect(fetchSimulado).toHaveBeenCalledTimes(5)
})
