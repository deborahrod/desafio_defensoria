import { render, screen, within } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App.tsx'

test('apresenta o cadastro entre cabeçalho e rodapé institucionais', () => {
  render(<App />)

  expect(screen.getByRole('heading', { level: 1, name: 'Cadastro de pessoa assistida' })).toBeInTheDocument()
  expect(screen.getByRole('banner')).toBeInTheDocument()
  expect(screen.getByRole('contentinfo')).toBeInTheDocument()
})

test('cabeçalho traz a marca da Defensoria e o selo de protótipo', () => {
  render(<App />)
  const cabecalho = within(screen.getByRole('banner'))

  const marca = cabecalho.getByRole('img', { name: 'Defensoria Pública do Estado de Goiás' })
  expect(marca).toHaveAttribute('src', '/dpe-logo.svg')
  expect(cabecalho.getByText('Protótipo')).toBeInTheDocument()
  expect(cabecalho.getByRole('link', { name: 'Portal da Defensoria' })).toHaveAttribute(
    'href',
    'https://www2.defensoria.go.def.br/',
  )
})

test('rodapé avisa que é uma demonstração sem validade oficial', () => {
  render(<App />)
  const rodape = within(screen.getByRole('contentinfo'))

  expect(rodape.getByText(/Protótipo de demonstração técnica/)).toBeInTheDocument()
})
