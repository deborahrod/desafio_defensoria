import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import { CONFIRMAR_ENTENDIMENTO, RELATO, corpoEnviado, estado, fetchSimulado, json, prepararFetch } from '../testes/fixtures.ts'
import Cadastro from './Cadastro.tsx'

prepararFetch()

async function comecar(inicial: ReturnType<typeof estado>) {
  fetchSimulado.mockResolvedValueOnce(json(inicial, 201))
  const usuario = userEvent.setup()
  render(<Cadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  return usuario
}

test('o relato tem campo de texto longo com aviso, contador e alternativa de responder pergunta por pergunta', async () => {
  const usuario = await comecar(estado(3, RELATO))

  const campo = await screen.findByLabelText('Sua resposta')
  expect(campo.tagName).toBe('TEXTAREA')
  expect(campo).toHaveAttribute('maxlength', '400')
  expect(campo).toHaveAccessibleDescription(/Não conte detalhes do seu caso.*0 de 400 caracteres/)
  expect(screen.getByRole('button', { name: 'Prefiro responder pergunta por pergunta' })).toBeInTheDocument()

  await usuario.type(campo, 'Moro em Anápolis')
  expect(screen.getByText(/16 de 400 caracteres/)).toBeInTheDocument()
})

test('no relato o Enter quebra a linha e Ctrl+Enter envia o texto', async () => {
  const usuario = await comecar(estado(3, RELATO))
  fetchSimulado.mockResolvedValueOnce(json(estado(4, null)))
  const campo = await screen.findByLabelText('Sua resposta')

  await usuario.type(campo, 'Moro em Anápolis{Enter}com minha mãe')
  expect(fetchSimulado).toHaveBeenCalledTimes(1)
  expect(campo).toHaveValue('Moro em Anápolis\ncom minha mãe')

  await usuario.keyboard('{Control>}{Enter}{/Control}')

  await waitFor(() => expect(fetchSimulado).toHaveBeenCalledTimes(2))
  expect(corpoEnviado(1)).toMatchObject({ versao: 3, valor: 'Moro em Anápolis\ncom minha mãe', opcao: null })
})

test('relato recusado pelo servidor mostra o motivo e mantém o texto no campo', async () => {
  const usuario = await comecar(estado(3, RELATO))
  fetchSimulado.mockResolvedValueOnce(
    json({ codigo: 'RESPOSTA_INVALIDA', mensagem: 'Para sua segurança, não conte detalhes do seu caso aqui.', campo: 'relato' }, 400),
  )
  const campo = await screen.findByLabelText('Sua resposta')

  await usuario.type(campo, 'Texto com detalhes')
  await usuario.click(screen.getByRole('button', { name: 'Enviar' }))

  await screen.findByText(/não conte detalhes do seu caso/)
  expect(screen.getByLabelText('Sua resposta')).toHaveValue('Texto com detalhes')
})

test('escolher responder pergunta por pergunta envia a opção, sem texto', async () => {
  const usuario = await comecar(estado(3, RELATO))
  fetchSimulado.mockResolvedValueOnce(json(estado(4, null)))

  await usuario.click(await screen.findByRole('button', { name: 'Prefiro responder pergunta por pergunta' }))

  await waitFor(() => expect(fetchSimulado).toHaveBeenCalledTimes(2))
  expect(corpoEnviado(1)).toMatchObject({ valor: null, opcao: 'PULAR_RELATO' })
})

test('a confirmação lista os dados entendidos e envia CONFIRMAR ao aceitar', async () => {
  const usuario = await comecar({ ...estado(4, CONFIRMAR_ENTENDIMENTO), estado: 'AGUARDANDO_ESCLARECIMENTO' })
  fetchSimulado.mockResolvedValueOnce(json(estado(5, null)))

  const lista = within(await screen.findByRole('list', { name: 'Dados entendidos' }))
  expect(lista.getAllByRole('listitem')).toHaveLength(2)
  expect(lista.getByText('Anápolis')).toBeInTheDocument()
  expect(lista.getByText(/Pedreiro \(trabalho informal\)/)).toBeInTheDocument()

  await usuario.click(screen.getByRole('button', { name: 'Sim, está certo' }))

  await waitFor(() => expect(fetchSimulado).toHaveBeenCalledTimes(2))
  expect(corpoEnviado(1)).toMatchObject({ opcao: 'CONFIRMAR', valor: null })
})

test('a ajuda do catálogo chega como mensagem da conversa e a pergunta continua aberta', async () => {
  const NUMERO = { ...RELATO, campo: 'quantidadeMembros', texto: 'Quantas pessoas moram na mesma casa?', ajuda: null, tipoEntrada: 'NUMERO' as const, opcoes: [], tamanhoMaximo: 2 }
  const usuario = await comecar(estado(7, NUMERO))
  fetchSimulado.mockResolvedValueOnce(json(estado(7, NUMERO, ['Ajuda: Dependentes são as pessoas da casa que precisam do seu sustento.'])))

  await usuario.type(await screen.findByLabelText('Sua resposta'), 'o que é dependente?{Enter}')

  await screen.findByText(/Dependentes são as pessoas da casa/)
  expect(screen.getByLabelText('Sua resposta')).toBeInTheDocument()
})
