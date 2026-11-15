import { createTheme } from '@mui/material/styles'

/** Tema do Material UI derivado dos tokens da Defensoria (ver tokens.css). */
export const temaMui = createTheme({
  palette: {
    primary: { main: '#007b2f', dark: '#006928', contrastText: '#ffffff' },
    text: { primary: '#4a4949', secondary: '#565656' },
  },
  typography: {
    fontFamily: "'Roboto', system-ui, -apple-system, 'Segoe UI', Arial, sans-serif",
  },
  components: {
    MuiStepLabel: {
      styleOverrides: {
        label: { fontSize: '0.875rem', color: '#565656', '&.Mui-active': { fontWeight: 700, color: '#4a4949' }, '&.Mui-completed': { color: '#4a4949' } },
      },
    },
    MuiStepIcon: {
      styleOverrides: {
        root: { color: '#cccccc', '&.Mui-active': { color: '#007b2f' }, '&.Mui-completed': { color: '#007b2f' } },
        text: { fill: '#ffffff', fontWeight: 700 },
      },
    },
    MuiStepConnector: {
      styleOverrides: {
        line: { borderColor: '#cccccc' },
      },
    },
  },
})
