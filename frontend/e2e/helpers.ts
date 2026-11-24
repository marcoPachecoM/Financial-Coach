import { expect, type Page } from '@playwright/test';

export const PASSWORD = 'correct horse battery staple';

export interface SignedUpUser {
  username: string;
  email: string;
  password: string;
  recoveryCodes: string[];
}

let counter = 0;

/** A user nobody else uses: tests do not depend on the demo data or on each other. */
export function uniqueUser() {
  const id = `${Date.now().toString(36)}${(counter++).toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  return { username: `e2e-${id}`, email: `e2e-${id}@example.test`, password: PASSWORD };
}

/** Signs up through the UI, reads the recovery codes from the dialog and enters the app. */
export async function signUp(page: Page): Promise<SignedUpUser> {
  const user = uniqueUser();
  await page.goto('/');
  await page.getByRole('button', { name: 'Create one' }).click();
  await page.getByLabel('Username').fill(user.username);
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(user.password);
  await page.getByLabel('Confirm password').fill(user.password);
  await page.getByRole('button', { name: 'Create account' }).click();

  const dialog = page.getByRole('dialog', { name: 'Save your recovery codes' });
  const rateLimited = page.getByRole('alert').filter({ hasText: 'Too many requests' });
  await expect(dialog.or(rateLimited)).toBeVisible();
  if (await rateLimited.isVisible()) {
    throw new Error(
      'Sign-up was rate limited. Start the backend with -Dspring-boot.run.profiles=dev,e2e ' +
        '(see "Tests" in the README); restarting it also clears the counters.',
    );
  }
  const recoveryCodes = await dialog.getByRole('listitem').allInnerTexts();
  expect(recoveryCodes).toHaveLength(8);

  const enter = dialog.getByRole('button', { name: 'Go to my dashboard' });
  await expect(enter).toBeDisabled();
  await dialog.getByLabel('I saved my recovery codes somewhere safe.').check();
  await enter.click();
  return { ...user, recoveryCodes: recoveryCodes.map((code) => code.trim()) };
}

export async function logOut(page: Page) {
  await page.getByRole('button', { name: /log out/i }).first().click();
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
}

export async function signIn(page: Page, email: string, password: string) {
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
}

/** "YYYY-MM-DD" of a day in the current month, so the Transactions view (current month) shows it. */
export function dayOfThisMonth(day: number) {
  const now = new Date();
  const safeDay = Math.min(day, now.getDate());
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(safeDay).padStart(2, '0')}`;
}
