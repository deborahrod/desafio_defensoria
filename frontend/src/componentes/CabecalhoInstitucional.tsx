import estilos from './CabecalhoInstitucional.module.css'

const PORTAL = 'https://www2.defensoria.go.def.br/'

export default function CabecalhoInstitucional() {
  return (
    <header className={estilos.cabecalho}>
      <div className={estilos.barra}>
        <div className={estilos.barraConteudo}>
          <a className={estilos.linkPortal} href={PORTAL} target="_blank" rel="noreferrer">
            Portal da Defensoria
          </a>
          <span className={estilos.selo}>Protótipo</span>
        </div>
      </div>
      <div className={estilos.principal}>
        <img
          className={estilos.logo}
          src="/dpe-logo.svg"
          alt="Defensoria Pública do Estado de Goiás"
          width="268"
          height="125"
        />
        <p className={estilos.servico}>Cadastro de pessoa assistida</p>
      </div>
    </header>
  )
}
