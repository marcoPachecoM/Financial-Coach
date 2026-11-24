import { expect, test } from '@playwright/test';
import { logOut, signIn, signUp } from './helpers';

test('sign up, then reset the password with a recovery code', async ({ page }) => {
  const user = await signUp(page);
  // A new user has no accounts yet.
  await expect(page.getByText('No accounts yet', { exact: true })).toBeVisible();

  await logOut(page);
  await page.getByRole('button', { name: 'Forgot password?' }).click();
  await page.getByLabel('Email address').fill(user.email);
  // Typed differently on purpose: case and dashes do not matter.
  await page.getByLabel('Recovery code').fill(user.recoveryCodes[0].toUpperCase().replaceAll('-', ' '));
  await page.getByLabel('New password').fill('a brand new passphrase');
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByRole('heading', { name: 'Password changed' })).toBeVisible();

  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await signIn(page, user.email, 'a brand new passphrase');
  await expect(page.getByText('No accounts yet', { exact: true })).toBeVisible();
});

test('a recovery code works only once', async ({ page }) => {
  const user = await signUp(page);
  await logOut(page);

  for (const [attempt, password] of [[1, 'first new passphrase'], [2, 'second new passphrase']] as const) {
    await page.getByRole('button', { name: 'Forgot password?' }).click();
    await page.getByLabel('Email address').fill(user.email);
    await page.getByLabel('Recovery code').fill(user.recoveryCodes[1]);
    await page.getByLabel('New password').fill(password);
    await page.getByRole('button', { name: 'Set new password' }).click();
    if (attempt === 1) {
      await expect(page.getByRole('heading', { name: 'Password changed' })).toBeVisible();
      await page.getByRole('button', { name: 'Back to sign in' }).click();
    }
  }
  await expect(page.getByRole('alert')).toHaveText('Invalid email or recovery code.');
});

test('a wrong password is rejected at sign-in', async ({ page }) => {
  const user = await signUp(page);
  await logOut(page);

  await signIn(page, user.email, 'not my password at all');
  await expect(page.getByText('Invalid email or password.')).toBeVisible();
});
