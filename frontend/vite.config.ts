/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    // Unit tests of plain functions: no browser needed. The browser tests live in e2e/ (Playwright).
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
