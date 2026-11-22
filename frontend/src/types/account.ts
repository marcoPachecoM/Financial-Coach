export interface Account {
  id: string;
  name: string;
  /** null when the account has no monthly budget. */
  monthlyBudget: number | null;
}
