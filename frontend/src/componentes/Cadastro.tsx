import { useConversa } from '../conversa/useConversa.ts'
import estilos from './Cadastro.module.css'
import ChatCadastro from './ChatCadastro.tsx'
import EtapasProgresso from './EtapasProgresso.tsx'
import PaginaInicial from './PaginaInicial.tsx'
import PainelRespostas from './PainelRespostas.tsx'

/** Começa na página explicativa e, depois de Começar, mostra etapas, conversa e respostas lado a lado. */
export default function Cadastro() {
  const conversa = useConversa()
  const { estado, encerrada, enviando, falha } = conversa

  if (!estado && !encerrada) {
    return <PaginaInicial aoComecar={conversa.iniciar} enviando={enviando} falha={falha} />
  }

  const mostrarPainel = estado && !encerrada && estado.estado !== 'CONFIRMADO'

  return (
    <div className={estilos.area}>
      {estado && !encerrada && <EtapasProgresso estado={estado} />}
      <div className={mostrarPainel ? estilos.colunas : estilos.coluna}>
        <ChatCadastro conversa={conversa} />
        {mostrarPainel && <PainelRespostas estado={estado} desabilitado={enviando} aoEditar={conversa.corrigir} />}
      </div>
    </div>
  )
}
