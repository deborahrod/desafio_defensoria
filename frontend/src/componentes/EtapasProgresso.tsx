import Step from '@mui/material/Step'
import StepLabel from '@mui/material/StepLabel'
import Stepper from '@mui/material/Stepper'
import type { EstadoConversa } from '../api/tipos.ts'
import estilos from './EtapasProgresso.module.css'

const REVISAO = 'Revisão'

/** Etapas do cadastro. O caminho muda conforme as respostas, então não há percentual. */
export default function EtapasProgresso({ estado }: { estado: EstadoConversa }) {
  const secoes = estado.progresso?.secoes ?? []
  if (secoes.length === 0) return null

  const etapas = [...secoes, REVISAO]
  const ativa =
    estado.estado === 'CONFIRMADO' ? etapas.length : estado.estado === 'EM_REVISAO' ? secoes.length : (estado.progresso?.etapa ?? 1) - 1

  return (
    <nav className={estilos.etapas} aria-label="Progresso do cadastro">
      <Stepper
        component="ol"
        alternativeLabel
        activeStep={ativa}
        sx={{
          // No celular só a etapa atual mostra o nome, para caber em 360 px.
          '& .MuiStepLabel-label:not(.Mui-active)': { display: { xs: 'none', md: 'block' } },
          '& .MuiStepLabel-label.Mui-active': { display: 'block' },
          padding: 0,
          margin: 0,
          listStyle: 'none',
        }}
      >
        {etapas.map((nome, indice) => (
          <Step key={nome} component="li" completed={indice < ativa} aria-current={indice === ativa ? 'step' : undefined}>
            <StepLabel>{nome}</StepLabel>
          </Step>
        ))}
      </Stepper>
    </nav>
  )
}
