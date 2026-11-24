import { expect, test, type Locator } from '@playwright/test';
import { signUp } from './helpers';

/** The drawer slides out with a CSS transform: off-screen, but still "visible" for Playwright. */
async function isOnScreen(locator: Locator) {
  const box = await locator.boundingBox();
  return box !== null && box.x + box.width > 0;
}

test('on a phone the sidebar is a drawer and nothing scrolls sideways', async ({ page }) => {
  await signUp(page);
  const goals = page.getByRole('button', { name: 'Goals', exact: true });
  expect(await isOnScreen(goals)).toBe(false);

  await page.getByRole('button', { name: 'Menu', exact: true }).click();
  await expect.poll(() => isOnScreen(goals)).toBe(true);
  await goals.click();

  await expect(page.getByRole('heading', { name: 'Goals', exact: true })).toBeVisible();
  // Choosing a view closes the drawer.
  await expect.poll(() => isOnScreen(goals)).toBe(false);

  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});
