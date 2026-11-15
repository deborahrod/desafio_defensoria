import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const api = process.env.API_URL ?? 'http://localhost:8081'

export default defineConfig({
  plugins: [react()],
  server: {
    // O Host original é mantido: com ele trocado, o CORS da API trataria a chamada como de outra origem.
    proxy: { '/api': { target: api, changeOrigin: false } },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/testes/configuracao.ts',
  },
})
