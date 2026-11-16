const FUSO = 'America/Sao_Paulo'

/**
 * Data de hoje no fuso de Brasília, no formato aaaa-mm-dd. É o fuso em que o servidor decide se uma data
 * de nascimento está no futuro, então a interface usa o mesmo para não aceitar nem recusar um dia a mais.
 */
export function dataDeHoje(agora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: FUSO, year: 'numeric', month: '2-digit', day: '2-digit' }).format(agora)
}
