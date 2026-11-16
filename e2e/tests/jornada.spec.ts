import { expect, test, type Page } from '@playwright/test'

/** CPF válido e novo a cada execução: o banco rejeita CPF repetido, e o Compose guarda os dados. */
function cpfNovo(): string {
  const base = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10))
  if (new Set(base).size === 1) base[0] = (base[0] + 1) % 10
  const digito = (numeros: number[]) => {
    const peso = numeros.length + 1
    const soma = numeros.reduce((total, n, i) => total + n * (peso - i), 0)
    const resto = (soma * 10) % 11
    return resto === 10 ? 0 : resto
  }
  const primeiro = digito(base)
  const segundo = digito([...base, primeiro])
  const todos = [...base, primeiro, segundo].join('')
  return `${todos.slice(0, 3)}.${todos.slice(3, 6)}.${todos.slice(6, 9)}-${todos.slice(9)}`
}

const botao = (pagina: Page, nome: string) => pagina.getByRole('button', { name: nome, exact: true })
const campo = (pagina: Page) => pagina.getByLabel('Sua resposta', { exact: true })

const mensagensDaPessoa = (pagina: Page) => pagina.locator('li', { hasText: 'Você:' })

/** Executa o passo e só segue quando a resposta aparece na conversa, ou seja, depois de o servidor aceitar. */
async function passo(pagina: Page, acao: () => Promise<void>) {
  const antes = await mensagensDaPessoa(pagina).count()
  await acao()
  await expect(mensagensDaPessoa(pagina)).toHaveCount(antes + 1)
}

const escolher = (pagina: Page, nome: string) => passo(pagina, () => botao(pagina, nome).click())

const digitar = (pagina: Page, texto: string) =>
  passo(pagina, async () => {
    await campo(pagina).fill(texto)
    await campo(pagina).press('Enter')
  })

test('jornada completa: da página inicial ao comprovante, com ausência, correção e confirmação', async ({ page }) => {
  const violacoesDeCsp: string[] = []
  page.on('console', (mensagem) => {
    if (mensagem.text().includes('Content Security Policy')) violacoesDeCsp.push(mensagem.text().slice(0, 160))
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Quem pode ser atendido' })).toBeVisible()
  await botao(page, 'Começar').click()

  // Identificação
  await escolher(page, 'Para mim')
  await digitar(page, 'Maria Fictícia da Silva')
  await escolher(page, 'Pular')
  await escolher(page, 'Não sei informar')
  await escolher(page, 'Sim, vou informar')
  await digitar(page, cpfNovo())

  // Contato
  await escolher(page, 'Por telefone')
  await digitar(page, '(62) 91234-5678')
  await escolher(page, 'Pular')

  // Localização, sem CEP: o endereço é preenchido à mão
  await digitar(page, 'Goiânia')
  await passo(page, async () => {
    await campo(page).selectOption('GO')
    await botao(page, 'Enviar').click()
  })
  await escolher(page, 'Sim')
  await escolher(page, 'Pular')
  await digitar(page, 'Rua das Flores Inventadas')
  await digitar(page, '123')
  await digitar(page, 'Setor Central')
  await escolher(page, 'Pular')

  // Situação socioeconômica
  await digitar(page, 'Diarista')
  await digitar(page, '1.518,35')
  await digitar(page, '3')
  await digitar(page, '2')
  await botao(page, 'Alugada').click()

  // A conferência ocupa a tela: sem conversa nem painel, com a ausência destacada por texto
  const titulo = page.getByRole('heading', { name: 'Revise seus dados' })
  await expect(titulo).toBeVisible()
  await expect(titulo).toBeFocused()
  await expect(page.getByRole('region', { name: 'Conversa do cadastro' })).toHaveCount(0)
  await expect(page.getByRole('complementary', { name: 'Suas respostas' })).toHaveCount(0)
  await expect(page.getByText('Ausência declarada')).toBeVisible()
  await expect(page.getByRole('note', { name: 'Informações pendentes' })).toContainText('Data de nascimento')

  // Correção com validação: telefone com DDD inexistente é barrado antes de ir ao servidor
  await page.getByRole('button', { name: 'Corrigir Telefone' }).click()
  const novoTelefone = page.getByLabel('Nova resposta: Telefone')
  await novoTelefone.fill('(02) 91234-5678')
  await novoTelefone.press('Enter')
  await expect(page.getByRole('alert')).toContainText('O DDD 02 não existe')
  await novoTelefone.fill('(62) 99876-5432')
  await novoTelefone.press('Enter')
  await expect(page.getByText('(62) 99876-5432')).toBeVisible()
  await expect(titulo).toBeFocused()

  // Confirmação e comprovante
  await botao(page, 'Confirmar cadastro').click()
  const comprovante = page.getByRole('heading', { name: 'Cadastro gravado' })
  await expect(comprovante).toBeVisible()
  await expect(comprovante).toBeFocused()
  await expect(page.getByText(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)).toBeVisible()
  await expect(page.getByText(/gravado com informações pendentes/)).toBeVisible()
  await expect(page.getByText(/Não é um protocolo da Defensoria/)).toBeVisible()

  // Encerrar limpa a sessão e volta ao início
  await botao(page, 'Encerrar e limpar a sessão').click()
  await expect(botao(page, 'Começar')).toBeVisible()

  expect(violacoesDeCsp, 'a política de conteúdo do nginx não pode bloquear a aplicação').toEqual([])
})
