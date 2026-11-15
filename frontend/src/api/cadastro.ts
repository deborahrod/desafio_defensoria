import { garantirTokenCsrf, requisitar } from './cliente.ts'
import type { Entrada, EstadoConversa } from './tipos.ts'

export async function iniciarSessao(): Promise<EstadoConversa> {
  await garantirTokenCsrf()
  return requisitar<EstadoConversa>('POST', '/sessao')
}

export function estadoAtual(): Promise<EstadoConversa> {
  return requisitar<EstadoConversa>('GET', '/atual')
}

export function responder(mensagemId: string, versao: number, entrada: Entrada): Promise<EstadoConversa> {
  return requisitar<EstadoConversa>('POST', '/respostas', {
    mensagemId,
    versao,
    valor: entrada.valor ?? null,
    opcao: entrada.opcao ?? null,
  })
}

export function encerrarSessao(): Promise<void> {
  return requisitar<void>('DELETE', '/sessao')
}
