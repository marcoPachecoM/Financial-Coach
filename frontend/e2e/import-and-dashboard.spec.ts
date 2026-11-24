import { expect, test } from '@playwright/test';
import { dayOfThisMonth, signUp } from './helpers';

/** Groups filled in (no LLM call) and a Tags column; dated this month so every view shows them. */
function bankCsv() {
  const header = 'Type,Date,Time,Name,Amount,Category Group,Category,Account,Notes,Tags';
  const rows = [
    `Expense,${dayOfThisMonth(1)} 09:00:00,09:00,Tacos El Gordo,250,Food,,Debit card,,trip-cancun`,
    `Expense,${dayOfThisMonth(2)} 12:30:00,12:30,Hotel Riu,1800,Travel,,Debit card,,trip-cancun`,
    `Expense,${dayOfThisMonth(3)} 20:00:00,20:00,Netflix,199,Services,Streaming,Debit card,,`,
    `Income,${dayOfThisMonth(1)} 08:00:00,08:00,ACME payroll,20000,Salary,,Debit card,,`,
  ];
  return [header, ...rows].join('\n');
}

test('import a CSV, see the dashboard and filter transactions by tag', async ({ page }) => {
  await signUp(page);

  await page.getByRole('button', { name: /import csv/i }).first().click();
  await page.getByRole('textbox').last().fill(bankCsv());
  await page.getByRole('button', { name: 'Import', exact: true }).click();
  await expect(page.getByText('4 transactions imported')).toBeVisible();
  await expect(page.getByText('Debit card', { exact: true }).first()).toBeVisible(); // the account was created
  await page.getByRole('button', { name: 'Done' }).click();

  // The copilot reports the import, and the dashboard has data.
  await expect(page.getByText(/Imported 4 transaction/)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Spending Trend' })).toBeVisible();
  await expect(page.getByText('Travel').first()).toBeVisible();

  // Transactions view: every row of the month, then only the tagged ones.
  await page.getByRole('button', { name: 'Transactions', exact: true }).click();
  await expect(page.getByText('Hotel Riu')).toBeVisible();
  await expect(page.getByText('Netflix').first()).toBeVisible();
  await page.getByRole('group', { name: 'Filter by tag' }).getByRole('button', { name: /trip-cancun/ }).click();
  await expect(page.getByText('Hotel Riu')).toBeVisible();
  await expect(page.getByText('Tacos El Gordo')).toBeVisible();
  await expect(page.getByText('Netflix')).toHaveCount(0);
});
