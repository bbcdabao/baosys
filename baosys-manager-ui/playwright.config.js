import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  fullyParallel: false,
  use: { baseURL: 'http://localhost:5178', headless: true, channel: process.env.PLAYWRIGHT_CHANNEL || 'msedge' },
  webServer: {
    command: 'npm run dev -- --host localhost --port 5178 --strictPort',
    url: 'http://localhost:5178/baosys-manager/tests/auth-harness.html',
    reuseExistingServer: false
  }
})
