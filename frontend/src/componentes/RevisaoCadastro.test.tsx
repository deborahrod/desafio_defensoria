import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { EstadoConversa, ItemResumo } from '../api/tipos.ts'
import ChatCadastro from './ChatCadastro.tsx'

const TELEFONE: ItemResumo = {
  campo: 'telefone',
  rotulo: 'Telefone',
  valor: '(62) 91234-5678',
  situacao: 'INFORMADO',
  edicao: { campo: 'telefone', texto: 'Qual número podemos usar?', ajuda: null, tipoEntrada: 'TELEFONE', opcoes: [], tamanhoMaximo: 20 },
}

const CPF: ItemResumo = {
  campo: 'situacaoCpf',
  rotulo: 'Tem CPF para informar',
  valor: 'Não possuo CPF',
  situacao: 'INFORMADO',
  edicao: {
    campo: 'situacaoCpf',
    texto: 'Você possui o CPF para informar agora?',
    ajuda: null,
    tipoEntrada: 'ESCOLHA',
    opcoes: [
      { id: 'INFORMADO', rotulo: 'Sim, vou informar' },
      { id: 'NAO_POSSUI', rotulo: 'Não possuo CPF' },
    ],
    tamanhoMaximo: null,
  },
}

const NASCIMENTO: ItemResumo = {
  campo: 'nascimento',
  rotulo: 'Data de nascimento',
  valor: 'Não soube informar',
  situacao: 'NAO_INFORMADO',
  edicao: { campo: 'nascimento', texto: 'Qual é a sua data de nascimento?', ajuda: null, tipoEntrada: 'DATA', opcoes: [], tamanhoMaximo: 10 },
}

function revisao(versao: number, sobrescrever: Partial<EstadoConversa> = {}): EstadoConversa {
  return {
    estado: 'EM_REVISAO',
    modoInteracao: 'ESTRUTURADO',
    versao,
    proximaPergunta: null,
    progresso: { etapa: 5, total: 5, secao: 'Revisão' },
    resumo: [
      { titulo: 'Identificação', itens: [NASCIMENTO, CPF] },
      { titulo: 'Contato', itens: [TELEFONE] },
    ],
    pendencias: ['Data de nascimento: Não soube informar', 'CPF: Não possuo CPF'],
    avisos: [],
    comprovante: null,
    expiraEm: '2026-11-15T12:30:00Z',
    minutosDeInatividade: 30,
    ...sobrescrever,
  }
}

function json(corpo: unknown, status = 200): Response {
  return new Response(JSON.stringify(corpo), { status, headers: { 'Content-Type': 'application/json' } })
}

const fetchSimulado = vi.fn<typeof fetch>()

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=token-de-teste'
  vi.stubGlobal('fetch', fetchSimulado)
})

afterEach(() => {
  fetchSimulado.mockReset()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

function corpoEnviado(chamada: number) {
  return JSON.parse(String(fetchSimulado.mock.calls[chamada][1]?.body))
}

async function abrirRevisao(estadoInicial = revisao(8)) {
  fetchSimulado.mockResolvedValueOnce(json(estadoInicial, 201))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  await screen.findByRole('heading', { name: 'Revise seus dados' })
  return usuario
}

test('o resumo mostra seções, valores explícitos, pendências e Corrigir em cada item', async () => {
  await abrirRevisao()

  const identificacao = within(screen.getByRole('region', { name: 'Identificação' }))
  expect(identificacao.getByText('Não soube informar')).toBeInTheDocument()
  expect(identificacao.getByText('Não possuo CPF')).toBeInTheDocument()
  const contato = within(screen.getByRole('region', { name: 'Contato' }))
  expect(contato.getByText('(62) 91234-5678')).toBeInTheDocument()
  expect(screen.getAllByRole('button', { name: /^Corrigir / })).toHaveLength(3)
  const pendencias = within(screen.getByRole('note', { name: 'Informações pendentes' }))
  expect(pendencias.getByText('Data de nascimento: Não soube informar')).toBeInTheDocument()
  expect(screen.getByText(/não significa que o atendimento foi aprovado/)).toBeInTheDocument()
})

test('Corrigir abre a entrada do tipo certo e envia só o campo escolhido com a versão revisada', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado.mockResolvedValueOnce(json(revisao(9)))

  await usuario.click(screen.getByRole('button', { name: 'Corrigir Telefone' }))
  expect(screen.getByText('Qual número podemos usar?')).toBeInTheDocument()
  await usuario.type(screen.getByLabelText('Sua resposta'), '(62) 99876-5432{Enter}')

  await waitFor(() => expect(fetchSimulado).toHaveBeenCalledTimes(2))
  const [url, opcoes] = fetchSimulado.mock.calls[1]
  expect(url).toBe('/api/v1/cadastro/campos/telefone')
  expect(opcoes?.method).toBe('PATCH')
  expect(corpoEnviado(1)).toEqual({ versao: 8, valor: '(62) 99876-5432', opcao: null })
  await waitFor(() => expect(screen.queryByLabelText('Sua resposta')).not.toBeInTheDocument())
})

test('Corrigir funciona só com o teclado', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado.mockResolvedValueOnce(json(revisao(9)))

  screen.getByRole('button', { name: 'Corrigir Tem CPF para informar' }).focus()
  await usuario.keyboard('{Enter}')
  const opcao = screen.getByRole('button', { name: 'Não possuo CPF', hidden: false })
  opcao.focus()
  await usuario.keyboard('{Enter}')

  await waitFor(() => expect(corpoEnviado(1)).toEqual({ versao: 8, valor: null, opcao: 'NAO_POSSUI' }))
})

test('correção recusada pelo servidor mostra o motivo e mantém a edição aberta', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado.mockResolvedValueOnce(
    json({ codigo: 'RESPOSTA_INVALIDA', mensagem: 'Informe o telefone com DDD, por exemplo (62) 91234-5678.', campo: 'telefone' }, 400),
  )

  await usuario.click(screen.getByRole('button', { name: 'Corrigir Telefone' }))
  await usuario.type(screen.getByLabelText('Sua resposta'), '123{Enter}')

  expect(await screen.findByRole('alert')).toHaveTextContent('Informe o telefone com DDD')
  expect(screen.getByLabelText('Sua resposta')).toBeInTheDocument()
})

test('Cancelar correção fecha a edição sem chamar o servidor', async () => {
  const usuario = await abrirRevisao()

  await usuario.click(screen.getByRole('button', { name: 'Corrigir Telefone' }))
  await usuario.click(screen.getByRole('button', { name: 'Cancelar correção' }))

  expect(screen.queryByLabelText('Sua resposta')).not.toBeInTheDocument()
  expect(fetchSimulado).toHaveBeenCalledTimes(1)
})

test('correção que pede confirmação para apagar dados volta para a pergunta de esclarecimento', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado.mockResolvedValueOnce(
    json({
      ...revisao(9),
      estado: 'AGUARDANDO_ESCLARECIMENTO',
      proximaPergunta: {
        campo: 'situacaoCpf',
        texto: 'Ao alterar "Tem CPF para informar", estas informações serão apagadas: CPF. Deseja continuar?',
        ajuda: null,
        tipoEntrada: 'ESCOLHA',
        opcoes: [
          { id: 'CONFIRMAR', rotulo: 'Sim, alterar' },
          { id: 'MANTER', rotulo: 'Não, manter como está' },
        ],
        tamanhoMaximo: null,
      },
    }),
  )

  await usuario.click(screen.getByRole('button', { name: 'Corrigir Tem CPF para informar' }))
  await usuario.click(screen.getByRole('button', { name: 'Sim, vou informar' }))

  expect(await screen.findByRole('button', { name: 'Sim, alterar' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Revise seus dados' })).not.toBeInTheDocument()
})

test('Confirmar cadastro grava uma vez com a versão revisada e mostra o comprovante', async () => {
  let concluir: (r: Response) => void = () => undefined
  const usuario = await abrirRevisao()
  fetchSimulado.mockReturnValueOnce(new Promise<Response>((resolve) => (concluir = resolve)))

  await usuario.dblClick(screen.getByRole('button', { name: 'Confirmar cadastro' }))

  expect(fetchSimulado).toHaveBeenCalledTimes(2)
  expect(screen.getByRole('button', { name: 'Confirmar cadastro' })).toBeDisabled()
  const [url, opcoes] = fetchSimulado.mock.calls[1]
  expect(url).toBe('/api/v1/cadastro/confirmacao')
  expect(opcoes?.method).toBe('POST')
  expect(corpoEnviado(1)).toMatchObject({ versaoRevisada: 8 })
  expect(corpoEnviado(1).chaveIdempotencia).toMatch(/^[0-9a-f-]{36}$/)

  concluir(
    json({
      ...revisao(8),
      estado: 'CONFIRMADO',
      resumo: [],
      pendencias: [],
      comprovante: { identificador: '0b8f1c0e-5f6a-4c53-9a7e-2f3d1f3c9a11', confirmadoEm: '2026-11-15T15:00:00Z', comPendencias: true },
    }),
  )

  const titulo = await screen.findByRole('heading', { name: 'Cadastro gravado' })
  await waitFor(() => expect(titulo).toHaveFocus())
  expect(screen.getByText('0b8f1c0e-5f6a-4c53-9a7e-2f3d1f3c9a11')).toBeInTheDocument()
  expect(screen.getByText(/15 de novembro de 2026/)).toBeInTheDocument()
  expect(screen.getByText(/gravado com informações pendentes/)).toBeInTheDocument()
  expect(screen.getByText(/Não é um protocolo da Defensoria/)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Cancelar cadastro' })).not.toBeInTheDocument()
})

test('falha de rede ao confirmar permite tentar de novo com a mesma chave de idempotência', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado
    .mockRejectedValueOnce(new TypeError('falha de rede'))
    .mockResolvedValueOnce(
      json({
        ...revisao(8),
        estado: 'CONFIRMADO',
        resumo: [],
        pendencias: [],
        comprovante: { identificador: 'abc', confirmadoEm: '2026-11-15T15:00:00Z', comPendencias: false },
      }),
    )

  await usuario.click(screen.getByRole('button', { name: 'Confirmar cadastro' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Não foi possível falar com o servidor')
  expect(screen.queryByRole('heading', { name: 'Cadastro gravado' })).not.toBeInTheDocument()
  await usuario.click(screen.getByRole('button', { name: 'Tentar novamente' }))

  await screen.findByRole('heading', { name: 'Cadastro gravado' })
  expect(corpoEnviado(2).chaveIdempotencia).toBe(corpoEnviado(1).chaveIdempotencia)
  expect(corpoEnviado(2).versaoRevisada).toBe(8)
})

test('conflito de CPF mostra orientação genérica e nenhum comprovante', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado.mockResolvedValueOnce(
    json(
      {
        codigo: 'CADASTRO_CONFLITANTE',
        mensagem: 'Não foi possível concluir a gravação deste cadastro e nada foi gravado. Procure o atendimento da Defensoria.',
      },
      409,
    ),
  )

  await usuario.click(screen.getByRole('button', { name: 'Confirmar cadastro' }))

  expect(await screen.findByRole('alert')).toHaveTextContent('nada foi gravado')
  expect(screen.queryByRole('heading', { name: 'Cadastro gravado' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Confirmar cadastro' })).toBeEnabled()
})

test('encerrar depois do comprovante limpa a sessão e volta ao início', async () => {
  const usuario = await abrirRevisao()
  fetchSimulado
    .mockResolvedValueOnce(
      json({
        ...revisao(8),
        estado: 'CONFIRMADO',
        resumo: [],
        pendencias: [],
        comprovante: { identificador: 'abc', confirmadoEm: '2026-11-15T15:00:00Z', comPendencias: false },
      }),
    )
    .mockResolvedValueOnce(new Response(null, { status: 204 }))

  await usuario.click(screen.getByRole('button', { name: 'Confirmar cadastro' }))
  await usuario.click(await screen.findByRole('button', { name: 'Encerrar e limpar a sessão' }))

  await screen.findByRole('button', { name: 'Começar' })
  expect(fetchSimulado.mock.calls[2][1]?.method).toBe('DELETE')
  expect(screen.queryByText('abc')).not.toBeInTheDocument()
})
