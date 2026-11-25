import { expect, test, type Locator } from '@playwright/test';
import { dayOfThisMonth, signUp } from './helpers';

/** The drawer slides out with a CSS transform: off-screen, but still "visible" for Playwright. */
async function isOnScreen(locator: Locator) {
  const box = await locator.boundingBox();
  return box !== null && box.x + box.width > 0;
}

test('on a phone the sidebar is a drawer and nothing scrolls sideways', async ({ page }) => {
  await signUp(page);
  const goals = page.getByRole('button', { name: 'Goals', exact: true });
  expect(await isOnScreen(goals)).toBe(false);

  // Even before any account exists, the header fits the viewport.
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0);

  // Import a CSV so all 5 header buttons (Add, Export, Import, Security, Log out) appear on mobile.
  await page.getByRole('button', { name: /import csv/i }).first().click();
  await page
    .getByRole('textbox')
    .last()
    .fill(
      [
        'Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes',
        `Expense,${dayOfThisMonth(1)} 09:00:00,09:00,Coffee,85,Food,,Credit Card,`,
      ].join('\n'),
    );
  await page.getByRole('button', { name: 'Import', exact: true }).click();
  await expect(page.getByText('1 transaction imported')).toBeVisible();
  await page.getByRole('button', { name: 'Done' }).click();

  await expect(page.getByRole('button', { name: 'Add transaction' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Export CSV' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(0);

  await page.getByRole('button', { name: 'Menu', exact: true }).click();
  await expect.poll(() => isOnScreen(goals)).toBe(true);
  await goals.click();

  await expect(page.getByRole('heading', { name: 'Goals', exact: true })).toBeVisible();
  // Choosing a view closes the drawer.
  await expect.poll(() => isOnScreen(goals)).toBe(false);

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});
