import { defineConfig, devices } from '@playwright/test';

/** Port of the Vite server the tests open; another one (E2E_PORT) avoids clashing with your own `npm run dev`. */
const port = process.env.E2E_PORT ?? '5173';
const baseURL = `http://localhost:${port}`;

/**
 * Browser tests of the whole app. The backend must already be running (see README → Tests):
 * these tests sign up real users through the API. Vite is started here when it is not running.
 */
export default defineConfig({
  testDir: 'e2e',
  // A failing test is retried once in CI only (to tell flaky from broken).
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    // When a test fails: a trace (every step, DOM and network) and a screenshot, in the HTML report.
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] }, testIgnore: /mobile\.spec\.ts/ },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /mobile\.spec\.ts/ },
  ],
  webServer: {
    // VITE_API_URL (the backend) comes from frontend/.env or from the environment.
    command: `npm run dev -- --port ${port} --strictPort`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
});
