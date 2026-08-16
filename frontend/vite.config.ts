import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    // e2e/ holds Playwright specs (a different test() from a different package) -- Vitest must
    // never try to collect them.
    exclude: ['node_modules/**', 'e2e/**'],
  },
})
