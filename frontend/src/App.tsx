import estilos from './App.module.css'
import Cadastro from './componentes/Cadastro.tsx'
import CabecalhoInstitucional from './componentes/CabecalhoInstitucional.tsx'
import RodapeInstitucional from './componentes/RodapeInstitucional.tsx'

export default function App() {
  return (
    <>
      <CabecalhoInstitucional />
      <main className={estilos.conteudo}>
        <h1 className={estilos.titulo}>Cadastro de pessoa assistida</h1>
        <Cadastro />
      </main>
      <RodapeInstitucional />
    </>
  )
}
