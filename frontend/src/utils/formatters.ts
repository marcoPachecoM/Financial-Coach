import { parseDateOnly } from './dateHelper';

export function formatCurrency(amount: number): string {
  const isNegative = amount < 0;
  const absAmount = Math.abs(amount);
  const formatted = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(absAmount);

  return isNegative ? `-${formatted}` : formatted;
}

export function formatDate(dateString: string): string {
  if (!dateString) return '';
  const d = parseDateOnly(dateString);
  if (d && !isNaN(d.getTime())) {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  return dateString;
}

export function formatFullDate(dateString: string): string {
  if (!dateString) return '';
  const d = parseDateOnly(dateString);
  if (d && !isNaN(d.getTime())) {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }
  return dateString;
}
