import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const api = process.env.API_URL ?? 'http://localhost:8081'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': api },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/testes/configuracao.ts',
  },
})
