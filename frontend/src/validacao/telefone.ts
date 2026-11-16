/** DDDs em uso no Brasil. A mesma lista do servidor. */
const DDDS_VALIDOS = new Set([
  '11', '12', '13', '14', '15', '16', '17', '18', '19',
  '21', '22', '24', '27', '28',
  '31', '32', '33', '34', '35', '37', '38',
  '41', '42', '43', '44', '45', '46', '47', '48', '49',
  '51', '53', '54', '55',
  '61', '62', '63', '64', '65', '66', '67', '68', '69',
  '71', '73', '74', '75', '77', '79',
  '81', '82', '83', '84', '85', '86', '87', '88', '89',
  '91', '92', '93', '94', '95', '96', '97', '98', '99',
])

const somenteDigitos = (texto: string) => texto.replace(/\D/g, '')

/** Máscara progressiva (62) 91234-5678 ou (62) 3201-0000. Número internacional (começa com +) fica como digitado. */
export function mascararTelefone(texto: string): string {
  if (texto.trimStart().startsWith('+')) return texto.replace(/[^\d+\s()-]/g, '')
  const d = somenteDigitos(texto).slice(0, 11)
  if (d.length === 0) return ''
  if (d.length <= 2) return `(${d}`
  const resto = d.slice(2)
  const corte = d.length > 10 ? 5 : 4
  return resto.length <= corte ? `(${d.slice(0, 2)}) ${resto}` : `(${d.slice(0, 2)}) ${resto.slice(0, corte)}-${resto.slice(corte)}`
}

function validarNacional(d: string): string | null {
  if (d.length !== 10 && d.length !== 11) {
    return 'Informe o telefone com DDD: 10 números para fixo ou 11 para celular, por exemplo (62) 91234-5678.'
  }
  if (!DDDS_VALIDOS.has(d.slice(0, 2))) return `O DDD ${d.slice(0, 2)} não existe. Confira os dois primeiros números.`
  const numero = d.slice(2)
  if (d.length === 11 && numero[0] !== '9') {
    return 'Celular tem 11 números e o primeiro depois do DDD é 9, por exemplo (62) 91234-5678.'
  }
  if (d.length === 10 && !'2345'.includes(numero[0])) {
    return 'Telefone fixo tem 10 números e começa com 2, 3, 4 ou 5 depois do DDD. Se for celular, use 11 números com 9 na frente.'
  }
  if (new Set(numero).size === 1) return 'Esse número parece inválido: todos os dígitos são iguais.'
  return null
}

/** Devolve a mensagem de erro, ou null quando o telefone é válido. As regras e os textos espelham o servidor. */
export function validarTelefone(texto: string): string | null {
  const limpo = texto.trim()
  let d = somenteDigitos(limpo)
  if (limpo.startsWith('+') && !d.startsWith('55')) {
    return d.length >= 8 && d.length <= 15 ? null : 'Informe o número com código do país e da região.'
  }
  if (limpo.startsWith('+')) d = d.slice(2)
  return validarNacional(d)
}
