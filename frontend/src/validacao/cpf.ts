const somenteDigitos = (texto: string) => texto.replace(/\D/g, '')

/** Máscara progressiva 000.000.000-00. */
export function mascararCpf(texto: string): string {
  const d = somenteDigitos(texto).slice(0, 11)
  if (d.length <= 3) return d
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`
}

function digitoVerificador(base: string): number {
  const peso = base.length + 1
  const soma = [...base].reduce((total, digito, i) => total + Number(digito) * (peso - i), 0)
  const resto = (soma * 10) % 11
  return resto === 10 ? 0 : resto
}

/** Devolve a mensagem de erro, ou null quando o CPF é válido. As regras e os textos espelham o servidor. */
export function validarCpf(texto: string): string | null {
  const d = somenteDigitos(texto)
  if (d.length !== 11) return `O CPF tem 11 números, e você informou ${d.length}. Confira e tente de novo.`
  if (new Set(d).size === 1) return 'CPF inválido: os 11 números são iguais. Confira o documento.'
  const confere = digitoVerificador(d.slice(0, 9)) === Number(d[9]) && digitoVerificador(d.slice(0, 10)) === Number(d[10])
  return confere ? null : 'CPF inválido: os dígitos verificadores não conferem. Confira os números.'
}
