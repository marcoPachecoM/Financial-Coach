import { expect, test } from '@playwright/test';
import { signUp } from './helpers';

test('create a goal, add money and see it in the panel', async ({ page }) => {
  await signUp(page);
  // Goals do not need an account: the view works for a brand new user.
  await page.getByRole('button', { name: 'Goals', exact: true }).click();
  await expect(page.getByText('No goals yet')).toBeVisible();

  const nextYear = new Date().getFullYear() + 1;
  await page.getByRole('button', { name: 'New goal' }).click();
  await page.getByLabel('What are you saving for?').fill('Trip to Japan');
  await page.getByLabel('Target amount').fill('40000');
  await page.getByLabel('Target date').fill(`${nextYear}-06-30`);
  await page.getByRole('button', { name: 'Create goal' }).click();

  const card = page.getByRole('article').filter({ hasText: 'Trip to Japan' });
  await expect(card).toContainText('$0.00 of $40,000.00');
  await expect(card).toContainText('Behind');

  await card.getByRole('button', { name: 'Add money' }).click();
  await page.getByLabel('Amount').fill('4000');
  await page.getByRole('button', { name: 'Add money' }).last().click();
  await expect(card).toContainText('$4,000.00 of $40,000.00');
  await expect(card).toContainText('10%');

  // The panel: history and chart.
  await card.getByRole('button', { name: 'Trip to Japan', exact: true }).click();
  const panel = page.getByRole('dialog', { name: 'Trip to Japan' });
  await expect(panel.getByText('+$4,000.00')).toBeVisible();
  await expect(panel.getByRole('img', { name: /Trip to Japan: \$4,000\.00 saved/ })).toBeVisible();

  // Withdrawing more than was saved is blocked before it reaches the server.
  await panel.getByRole('button', { name: 'Add money' }).click();
  await page.getByRole('radio', { name: 'Withdraw' }).click();
  const amount = page.getByLabel('Amount');
  await amount.fill('5000');
  await page.getByRole('button', { name: 'Withdraw', exact: true }).last().click();
  expect(await amount.evaluate((input: HTMLInputElement) => input.validity.rangeOverflow)).toBe(true);
});
