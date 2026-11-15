import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import { PARA_QUEM, estado, fetchSimulado, json, prepararFetch } from '../testes/fixtures.ts'
import Cadastro from './Cadastro.tsx'

prepararFetch()

test('a primeira página explica quem pode ser atendido em três blocos e aponta para a fonte oficial', () => {
  render(<Cadastro />)

  expect(screen.getByRole('heading', { level: 2, name: 'Quem pode ser atendido' })).toBeInTheDocument()
  const blocos = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
  expect(blocos).toEqual(['Renda e vulnerabilidade', 'Proteção de direitos', 'Sistema de justiça'])
  expect(screen.getByText(/Pessoas de baixa renda/)).toBeInTheDocument()
  expect(screen.getByText(/Mulheres em situação de violência/)).toBeInTheDocument()
  expect(screen.getByText(/Presos e seus familiares/)).toBeInTheDocument()
  const fonte = screen.getByRole('link', { name: /página oficial/ })
  expect(fonte).toHaveAttribute('href', 'https://www2.defensoria.go.def.br/ser-atendido')
  expect(fonte).toHaveAttribute('target', '_blank')
  expect(fonte).toHaveAttribute('rel', expect.stringContaining('noreferrer'))
})

test('antes de começar avisa que é protótipo com dados fictícios e que a IA é opcional', () => {
  render(<Cadastro />)

  const antes = within(screen.getByRole('region', { name: 'Antes de começar' }))
  expect(antes.getByText(/apenas dados fictícios/)).toBeInTheDocument()
  expect(antes.getByText(/escolhe durante o cadastro se quer usá-la/)).toBeInTheDocument()
  expect(antes.getByRole('button', { name: 'Começar' })).toBeInTheDocument()
})

test('Começar troca a página explicativa pelas etapas, a conversa e o painel de respostas', async () => {
  fetchSimulado.mockResolvedValueOnce(json(estado(0, PARA_QUEM), 201))
  const usuario = userEvent.setup()
  render(<Cadastro />)

  await usuario.click(screen.getByRole('button', { name: 'Começar' }))

  expect(await screen.findByText(PARA_QUEM.texto)).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Quem pode ser atendido' })).not.toBeInTheDocument()
  expect(screen.getByRole('navigation', { name: 'Progresso do cadastro' })).toBeInTheDocument()
  expect(screen.getByRole('complementary', { name: 'Suas respostas' })).toBeInTheDocument()
  expect(screen.getByText(/As respostas aparecem aqui/)).toBeInTheDocument()
})
