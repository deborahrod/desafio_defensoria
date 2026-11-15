import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import type { EstadoConversa, ProximaPergunta } from '../api/tipos.ts'
import ChatCadastro from './ChatCadastro.tsx'

const PARA_QUEM: ProximaPergunta = {
  campo: 'paraQuem',
  texto: 'Este cadastro é para você ou para outra pessoa?',
  ajuda: null,
  tipoEntrada: 'ESCOLHA',
  opcoes: [
    { id: 'PROPRIA_PESSOA', rotulo: 'Para mim' },
    { id: 'OUTRA_PESSOA', rotulo: 'Para outra pessoa' },
  ],
  tamanhoMaximo: null,
}

const NOME: ProximaPergunta = {
  campo: 'nome',
  texto: 'Como você se chama?',
  ajuda: null,
  tipoEntrada: 'TEXTO',
  opcoes: [],
  tamanhoMaximo: 200,
}

function estado(versao: number, pergunta: ProximaPergunta | null, avisos: string[] = []): EstadoConversa {
  return {
    estado: pergunta ? 'EM_PREENCHIMENTO' : 'EM_REVISAO',
    modoInteracao: 'ESTRUTURADO',
    versao,
    proximaPergunta: pergunta,
    progresso: { etapa: 1, total: 5, secao: 'Quem preenche' },
    resumo: [],
    pendencias: [],
    avisos,
    comprovante: null,
    expiraEm: '2026-11-15T12:30:00Z',
    minutosDeInatividade: 30,
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

async function comecar() {
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  await screen.findByText(PARA_QUEM.texto)
  return usuario
}

test('Começar abre a sessão e mostra uma pergunta por vez, com avisos e progresso', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, PARA_QUEM, ['Vou fazer algumas perguntas.']), 201))

  await comecar()

  expect(screen.getByText('Vou fazer algumas perguntas.')).toBeInTheDocument()
  expect(screen.getByText('Etapa 1 de 5: Quem preenche')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Para mim' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Para outra pessoa' })).toBeInTheDocument()
  const [url, opcoes] = fetchSimulado.mock.calls[0]
  expect(url).toBe('/api/v1/cadastro/sessao')
  expect(opcoes?.method).toBe('POST')
  expect(new Headers(opcoes?.headers).get('X-XSRF-TOKEN')).toBe('token-de-teste')
})

test('escolher uma opção envia mensagemId e versão e mostra a próxima pergunta', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201)).mockResolvedValueOnce(json(estado(1, NOME)))
  const usuario = await comecar()

  await usuario.click(screen.getByRole('button', { name: 'Para mim' }))

  await screen.findByText('Como você se chama?')
  expect(corpoEnviado(1)).toMatchObject({ versao: 0, opcao: 'PROPRIA_PESSOA', valor: null })
  expect(corpoEnviado(1).mensagemId).toMatch(/^[0-9a-f-]{36}$/)
  expect(screen.getByText('Para mim', { selector: 'li' })).toBeInTheDocument()
})

test('texto livre é enviado com Enter, sem usar o mouse', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, NOME), 201)).mockResolvedValueOnce(json(estado(1, null)))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))
  const campo = await screen.findByLabelText('Sua resposta')
  await waitFor(() => expect(campo).toHaveFocus())

  await usuario.type(campo, 'Ana Fictícia{Enter}')

  await screen.findByRole('heading', { name: 'Revise seus dados' })
  expect(corpoEnviado(1)).toMatchObject({ valor: 'Ana Fictícia', opcao: null })
})

test('duplo clique envia uma única mensagem e o botão fica bloqueado durante o envio', async () => {
  let concluir: (r: Response) => void = () => undefined
  fetchSimulado
    .mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))
    .mockReturnValueOnce(new Promise<Response>((resolve) => (concluir = resolve)))
  const usuario = await comecar()

  const botao = screen.getByRole('button', { name: 'Para mim' })
  await usuario.dblClick(botao)

  expect(fetchSimulado).toHaveBeenCalledTimes(2)
  expect(screen.getByRole('button', { name: 'Para mim' })).toBeDisabled()
  expect(screen.getByRole('status')).toHaveTextContent('Enviando')

  concluir(json(estado(1, NOME)))
  await screen.findByText('Como você se chama?')
})

test('falha de rede preserva a resposta e a nova tentativa reaproveita o mesmo mensagemId', async () => {
  fetchSimulado
    .mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))
    .mockRejectedValueOnce(new TypeError('falha de rede'))
    .mockResolvedValueOnce(json(estado(1, NOME)))
  const usuario = await comecar()

  await usuario.click(screen.getByRole('button', { name: 'Para mim' }))
  const alerta = await screen.findByRole('alert')
  expect(alerta).toHaveTextContent('Não foi possível falar com o servidor')
  expect(screen.getByText(PARA_QUEM.texto)).toBeInTheDocument()

  await usuario.click(screen.getByRole('button', { name: 'Tentar novamente' }))

  await screen.findByText('Como você se chama?')
  expect(corpoEnviado(2).mensagemId).toBe(corpoEnviado(1).mensagemId)
  expect(corpoEnviado(2).versao).toBe(corpoEnviado(1).versao)
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('resposta recusada pelo servidor aparece na conversa e a pergunta continua', async () => {
  fetchSimulado
    .mockResolvedValueOnce(json(estado(0, NOME), 201))
    .mockResolvedValueOnce(json({ codigo: 'RESPOSTA_INVALIDA', mensagem: 'Preciso que você informe o nome para continuar.', campo: 'nome' }, 400))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))

  await usuario.type(await screen.findByLabelText('Sua resposta'), '12345{Enter}')

  await screen.findByText(/Preciso que você informe o nome/)
  expect(screen.getByLabelText('Sua resposta')).toBeInTheDocument()
})

test('texto digitado é exibido como texto, sem interpretar HTML', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, NOME), 201)).mockResolvedValueOnce(json(estado(1, null)))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))

  await usuario.type(await screen.findByLabelText('Sua resposta'), '<img src=x onerror=alert(1)>{Enter}')

  await screen.findByText('<img src=x onerror=alert(1)>', { selector: 'li' })
  expect(document.querySelector('img[src="x"]')).toBeNull()
})

test('sessão expirada mostra o aviso e oferece novo cadastro sem exibir dados antigos', async () => {
  fetchSimulado
    .mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))
    .mockResolvedValueOnce(json({ codigo: 'SESSAO_EXPIRADA', mensagem: 'Sua sessão expirou por inatividade.' }, 410))
  const usuario = await comecar()

  await usuario.click(screen.getByRole('button', { name: 'Para mim' }))

  await screen.findByText('Sua sessão expirou por inatividade.')
  expect(screen.getByRole('button', { name: 'Começar um novo cadastro' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Para mim' })).not.toBeInTheDocument()
})

test('cancelar pede confirmação antes de apagar o cadastro', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201)).mockResolvedValueOnce(new Response(null, { status: 204 }))
  const usuario = await comecar()

  await usuario.click(screen.getByRole('button', { name: 'Cancelar cadastro' }))
  expect(fetchSimulado).toHaveBeenCalledTimes(1)
  expect(screen.getByText(/nada será gravado/)).toBeInTheDocument()

  await usuario.click(screen.getByRole('button', { name: 'Sim, sair e apagar' }))

  await screen.findByRole('button', { name: 'Começar' })
  expect(fetchSimulado.mock.calls[1][1]?.method).toBe('DELETE')
})

test('mostra se a conversa usa IA e avisa que a interpretação pode demorar', async () => {
  let concluir: (r: Response) => void = () => undefined
  fetchSimulado
    .mockResolvedValueOnce(json({ ...estado(0, NOME), modoInteracao: 'ASSISTIDO_POR_IA' }, 201))
    .mockReturnValueOnce(new Promise<Response>((resolve) => (concluir = resolve)))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))

  expect(await screen.findByText(/Assistência por IA ativa/)).toBeInTheDocument()
  await usuario.type(screen.getByLabelText('Sua resposta'), 'Moro em Goiânia{Enter}')
  expect(screen.getByRole('status')).toHaveTextContent('interpretação por IA pode levar alguns segundos')

  concluir(json({ ...estado(1, null), modoInteracao: 'ASSISTIDO_POR_IA' }))
  await screen.findByRole('heading', { name: 'Revise seus dados' })
})

test('sem IA a conversa informa que as perguntas são diretas', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))

  await comecar()

  expect(screen.getByText('Perguntas diretas, sem uso de IA.')).toBeInTheDocument()
})

test('aviso de falha da IA chega como mensagem da conversa e o modo volta a ser direto', async () => {
  fetchSimulado
    .mockResolvedValueOnce(json({ ...estado(0, NOME), modoInteracao: 'ASSISTIDO_POR_IA' }, 201))
    .mockResolvedValueOnce(json(estado(0, NOME, ['A assistência por IA está indisponível no momento.'])))
  const usuario = userEvent.setup()
  render(<ChatCadastro />)
  await usuario.click(screen.getByRole('button', { name: 'Começar' }))

  await usuario.type(await screen.findByLabelText('Sua resposta'), 'Moro em Goiânia{Enter}')

  await screen.findByText('A assistência por IA está indisponível no momento.')
  expect(screen.getByText('Perguntas diretas, sem uso de IA.')).toBeInTheDocument()
  expect(screen.getByLabelText('Sua resposta')).toBeInTheDocument()
})

test('limite de respostas mostra o aviso e permite tentar de novo com o mesmo mensagemId', async () => {
  fetchSimulado
    .mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))
    .mockResolvedValueOnce(json({ codigo: 'LIMITE_EXCEDIDO', mensagem: 'Muitas respostas em pouco tempo. Aguarde 20 segundos e tente de novo.' }, 429))
    .mockResolvedValueOnce(json(estado(1, NOME)))
  const usuario = await comecar()

  await usuario.click(screen.getByRole('button', { name: 'Para mim' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Aguarde 20 segundos')
  expect(screen.getByText(PARA_QUEM.texto)).toBeInTheDocument()
  await usuario.click(screen.getByRole('button', { name: 'Tentar novamente' }))

  await screen.findByText('Como você se chama?')
  expect(corpoEnviado(2).mensagemId).toBe(corpoEnviado(1).mensagemId)
})
