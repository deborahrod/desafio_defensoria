import estilos from './RodapeInstitucional.module.css'

export default function RodapeInstitucional() {
  return (
    <footer className={estilos.rodape}>
      <div className={estilos.conteudo}>
        <img
          className={estilos.logo}
          src="/dpe-logo.svg"
          alt="Defensoria Pública do Estado de Goiás"
          width="268"
          height="125"
        />
        <div>
          <h2 className={estilos.titulo}>Localização</h2>
          <p>Alameda Cel. Joaquim de Bastos, nº 282, Qd. 217, Lt. 14</p>
          <p>Setor Marista, Goiânia. CEP 74175-150</p>
        </div>
        <div>
          <h2 className={estilos.titulo}>Horário de funcionamento</h2>
          <p>8 às 18 horas</p>
          <p>De segunda a sexta-feira</p>
        </div>
      </div>
      <p className={estilos.aviso}>
        Protótipo de demonstração técnica com dados fictícios. Não é um serviço oficial em operação.
      </p>
    </footer>
  )
}
