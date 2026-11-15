import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import App from './App.tsx'

test('identifica o cadastro e a Defensoria', () => {
  render(<App />)

  expect(screen.getByRole('heading', { name: 'Cadastro de pessoa assistida' })).toBeInTheDocument()
  expect(screen.getByText('Defensoria Pública do Estado de Goiás')).toBeInTheDocument()
})
