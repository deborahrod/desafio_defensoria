import { useConversa } from '../conversa/useConversa.ts'
import estilos from './Cadastro.module.css'
import ChatCadastro from './ChatCadastro.tsx'
import ComprovanteCadastro from './ComprovanteCadastro.tsx'
import EtapasProgresso from './EtapasProgresso.tsx'
import PaginaInicial from './PaginaInicial.tsx'
import PainelRespostas from './PainelRespostas.tsx'
import TelaConferencia from './TelaConferencia.tsx'

/**
 * Começa na página explicativa. Durante o preenchimento mostra etapas, conversa e respostas lado a lado. Ao fim,
 * a conferência e o comprovante ocupam a tela inteira, fora da conversa.
 */
export default function Cadastro() {
  const conversa = useConversa()
  const { estado, encerrada, enviando, falha } = conversa

  if (!estado && !encerrada) {
    return <PaginaInicial aoComecar={conversa.iniciar} enviando={enviando} falha={falha} />
  }

  const mostrarPainel = estado && !encerrada && estado.estado !== 'CONFIRMADO' && estado.estado !== 'EM_REVISAO'
  const emConferencia = estado?.estado === 'EM_REVISAO' && !encerrada
  const comprovante = estado?.estado === 'CONFIRMADO' && !encerrada ? estado.comprovante : null

  return (
    <div className={estilos.area}>
      {estado && !encerrada && <EtapasProgresso estado={estado} />}
      {emConferencia ? (
        <TelaConferencia
          estado={estado}
          enviando={enviando}
          falha={falha}
          recusa={conversa.recusa}
          aoCorrigir={conversa.corrigir}
          aoConfirmar={conversa.confirmar}
          aoTentarNovamente={conversa.tentarNovamente}
          aoCancelar={conversa.cancelar}
        />
      ) : comprovante ? (
        <ComprovanteCadastro comprovante={comprovante} desabilitado={enviando} aoEncerrar={conversa.cancelar} />
      ) : (
        <div className={mostrarPainel ? estilos.colunas : estilos.coluna}>
          <ChatCadastro conversa={conversa} />
          {mostrarPainel && <PainelRespostas estado={estado} desabilitado={enviando} aoEditar={conversa.corrigir} />}
        </div>
      )}
    </div>
  )
}
