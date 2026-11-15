import estilos from './App.module.css'
import CabecalhoInstitucional from './componentes/CabecalhoInstitucional.tsx'
import RodapeInstitucional from './componentes/RodapeInstitucional.tsx'

export default function App() {
  return (
    <>
      <CabecalhoInstitucional />
      <main className={estilos.conteudo}>
        <h1 className={estilos.titulo}>Cadastro de pessoa assistida</h1>
        <div className={estilos.apresentacao}>
          <p>
            Aqui você informa seus dados para solicitar atendimento na Defensoria Pública do Estado
            de Goiás. Será uma conversa com uma pergunta por vez, e você poderá revisar tudo antes
            de confirmar.
          </p>
          <p>
            Esta é uma demonstração técnica. Use apenas dados fictícios: o cadastro não tem validade
            oficial.
          </p>
        </div>
      </main>
      <RodapeInstitucional />
    </>
  )
}
