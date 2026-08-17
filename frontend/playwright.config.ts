import { defineConfig, devices } from '@playwright/test'

/**
 * Minimal, high-value E2E coverage (CLAUDE.md 55-57). Runs against a local frontend dev server
 * and expects a real backend + Postgres already running (see docs/e2e-testing.md) -- it never
 * hits any external payer API, and it does not run destructive operations against shared data.
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
