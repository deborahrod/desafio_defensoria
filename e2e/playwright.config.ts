import { defineConfig } from '@playwright/test'

// Roda contra o stack já no ar (docker compose up). Os arquivos rodam em ordem e um por vez, porque
// compartilham o mesmo teto por origem: a jornada vem antes e a checagem do teto, depois.
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    // Na CI o Chromium vem do próprio Playwright. Local, usa o Chrome instalado.
    channel: process.env.CI ? undefined : 'chrome',
    trace: 'retain-on-failure',
  },
})
