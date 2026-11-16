import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import type { EstadoConversa } from '../api/tipos.ts'
import { NOME, NASCIMENTO, TELEFONE, corpoEnviado, estado, fetchSimulado, json, prepararFetch, revisao } from '../testes/fixtures.ts'
import Cadastro from './Cadastro.tsx'

prepararFetch()

/** Conversa em andamento: duas respostas já aceitas e a próxima pergunta em aberto. */
function emAndamento(versao: number, sobrescrever: Partial<EstadoConversa> = {}): EstadoConversa {
  return {
    ...estado(versao, NOME),
    progresso: { etapa: 3, total: 5, secao: 'Contato', secoes: ['Quem preenche', 'Identificação', 'Contato', 'Localização', 'Situação socioeconômica'] },
    resumo: [
      { titulo: 'Identificação', itens: [NASCIMENTO] },
      { titulo: 'Contato', itens: [TELEFONE] },
    ],
    ...sobrescrever,
  }
}

async function comecar(inicial: EstadoConversa) {
  fetchSimulado.mockResolvedValueOnce(json(inicial, 201))
  const usuario = userEvent.setup()
  render(<Cadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  await screen.findByRole('complementary', { name: 'Suas respostas' })
  return usuario
}

test('as respostas aceitas aparecem ao lado da conversa, agrupadas por seção', async () => {
  await comecar(emAndamento(4))

  const painel = within(screen.getByRole('complementary', { name: 'Suas respostas' }))
  expect(within(painel.getByRole('region', { name: 'Contato' })).getByText('(62) 91234-5678')).toBeInTheDocument()
  expect(within(painel.getByRole('region', { name: 'Identificação' })).getByText('Não soube informar')).toBeInTheDocument()
  expect(painel.getAllByRole('button', { name: /^Editar / })).toHaveLength(2)
  expect(painel.getByRole('button', { name: /Mostrar \(2\)/ })).toBeInTheDocument()
})

test('dá para editar uma resposta no meio do preenchimento, sem perder a pergunta em andamento', async () => {
  const usuario = await comecar(emAndamento(4))
  fetchSimulado.mockResolvedValueOnce(json(emAndamento(5)))

  await usuario.click(screen.getByRole('button', { name: 'Editar Telefone' }))
  const painel = within(screen.getByRole('complementary', { name: 'Suas respostas' }))
  expect(painel.getByText('Qual número podemos usar?')).toBeInTheDocument()
  await usuario.type(painel.getByLabelText('Nova resposta: Telefone'), '(62) 99876-5432{Enter}')

  await waitFor(() => expect(fetchSimulado).toHaveBeenCalledTimes(2))
  const [url, opcoes] = fetchSimulado.mock.calls[1]
  expect(url).toBe('/api/v1/cadastro/campos/telefone')
  expect(opcoes?.method).toBe('PATCH')
  expect(corpoEnviado(1)).toEqual({ versao: 4, valor: '(62) 99876-5432', opcao: null })
  await waitFor(() => expect(painel.queryByLabelText('Nova resposta: Telefone')).not.toBeInTheDocument())
  // A pergunta em aberto continua à espera, e é repetida na conversa depois da edição.
  expect(screen.getAllByText(NOME.texto, { selector: 'li' }).length).toBeGreaterThanOrEqual(1)
  expect(screen.getByLabelText('Sua resposta')).toBeInTheDocument()
})

test('na conferência final o painel e a conversa saem de cena e só a tela de conferência confirma', async () => {
  fetchSimulado.mockResolvedValueOnce(json(revisao(8), 201))
  render(<Cadastro />)
  await userEvent.setup().click(screen.getByRole('button', { name: 'Começar' }))
  await screen.findByRole('heading', { name: 'Revise seus dados' })

  expect(screen.getByRole('heading', { name: 'Revise seus dados' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Confirmar cadastro' })).toBeInTheDocument()
  expect(screen.queryByRole('complementary', { name: 'Suas respostas' })).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Conversa do cadastro' })).not.toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: /^Corrigir / })).toHaveLength(3)
})

test('com uma escolha pendente do servidor as edições ficam bloqueadas', async () => {
  await comecar(emAndamento(4, { estado: 'AGUARDANDO_ESCLARECIMENTO' }))

  for (const botao of screen.getAllByRole('button', { name: /^Editar / })) {
    expect(botao).toBeDisabled()
  }
})

test('o painel pode ser aberto e fechado no celular, e informa o estado ao leitor de tela', async () => {
  const usuario = await comecar(emAndamento(4))
  const alternar = screen.getByRole('button', { name: /Mostrar \(2\)/ })
  expect(alternar).toHaveAttribute('aria-expanded', 'false')

  await usuario.click(alternar)

  expect(screen.getByRole('button', { name: /Ocultar \(2\)/ })).toHaveAttribute('aria-expanded', 'true')
})

test('edição recusada pelo servidor mostra o motivo e mantém o editor aberto', async () => {
  const usuario = await comecar(emAndamento(4))
  fetchSimulado.mockResolvedValueOnce(json({ codigo: 'RESPOSTA_INVALIDA', mensagem: 'Informe o telefone com DDD.', campo: 'telefone' }, 400))

  await usuario.click(screen.getByRole('button', { name: 'Editar Telefone' }))
  await usuario.type(screen.getByLabelText('Nova resposta: Telefone'), '62912345678{Enter}')

  expect(await screen.findByRole('alert')).toHaveTextContent('Informe o telefone com DDD.')
  expect(screen.getByLabelText('Nova resposta: Telefone')).toBeInTheDocument()
})
