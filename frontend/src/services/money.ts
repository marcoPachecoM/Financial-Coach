/** The API sends money as strings ("147.06") so no precision is lost; the UI only needs numbers to display. */
export const toNumber = (value: string): number => Number(value);

export const toNumberOrNull = (value: string | null): number | null =>
  value === null ? null : Number(value);
