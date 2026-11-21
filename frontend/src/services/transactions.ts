import api from "./instance";
import { toNumber } from "./money";
import type {
  ApiTransaction,
  ApiTransactionCreateRequest,
  ApiTransactionPage,
  CsvUploadResponse,
  RecurrenceFrequency,
  TransactionType,
} from "../types/api";
import type { Transaction } from "../types/transaction";

/** Largest page the API allows. */
const PAGE_SIZE = 100;
/** Safety limit for the dashboard: 50 pages = 5,000 transactions. */
const MAX_PAGES = 50;

export interface TransactionFilters {
  accountId: string;
  /** Inclusive dates "YYYY-MM-DD"; omitted means no limit. */
  startDate?: string;
  endDate?: string;
}

const toTransaction = (tx: ApiTransaction): Transaction => ({
  id: tx.id,
  accountId: tx.account_id,
  type: tx.type,
  datetime: tx.datetime,
  name: tx.name,
  // The API stores expenses positive and income negative; the UI uses cash flow.
  amount: -toNumber(tx.amount),
  categoryGroup: tx.category_group,
  category: tx.category,
  notes: tx.notes,
  recurringTransactionId: tx.recurring_transaction_id,
});

/** What the "Add transaction" form collects. */
export interface NewTransaction {
  accountId: string;
  type: TransactionType;
  /** "YYYY-MM-DDTHH:mm" from the datetime-local input. */
  datetime: string;
  name: string;
  /** Positive figure; the sign is applied from `type`. */
  amount: number;
  /** Empty: assigned automatically by the backend. */
  categoryGroup?: string;
  category?: string;
  notes?: string;
  recurrence?: { frequency: RecurrenceFrequency; endDate?: string };
}

/** The API wants EXPENSE positive and INCOME negative (the form only asks for the figure). */
const signedAmount = (type: TransactionType, amount: number) =>
  (type === "EXPENSE" ? Math.abs(amount) : -Math.abs(amount)).toFixed(2);

export const createTransaction = async (input: NewTransaction): Promise<Transaction> => {
  const body: ApiTransactionCreateRequest = {
    type: input.type,
    datetime: input.datetime,
    name: input.name,
    amount: signedAmount(input.type, input.amount),
    account_id: input.accountId,
    ...(input.categoryGroup ? { category_group: input.categoryGroup } : {}),
    ...(input.category?.trim() ? { category: input.category.trim() } : {}),
    ...(input.notes?.trim() ? { notes: input.notes.trim() } : {}),
    ...(input.recurrence
      ? {
          recurrence: {
            frequency: input.recurrence.frequency,
            ...(input.recurrence.endDate ? { end_date: input.recurrence.endDate } : {}),
          },
        }
      : {}),
  };
  const res = await api.post<ApiTransaction>("/transactions", body);
  return toTransaction(res.data);
};

/** Soft delete: an occurrence of a recurring transaction is not created again. */
export const deleteTransaction = async (id: string): Promise<void> => {
  await api.delete(`/transactions/${id}`);
};

/**
 * Every transaction of the account in the period, newest first. The charts need the whole
 * period, so pages are fetched until the API says there are no more.
 */
export const getAllTransactions = async ({
  accountId,
  startDate,
  endDate,
}: TransactionFilters): Promise<Transaction[]> => {
  const all: Transaction[] = [];
  for (let page = 1; page <= MAX_PAGES; page++) {
    const res = await api.get<ApiTransactionPage>("/transactions", {
      params: { account_id: accountId, start_date: startDate, end_date: endDate, page, size: PAGE_SIZE },
    });
    all.push(...res.data.content.map(toTransaction));
    if (!res.data.has_more) {
      break;
    }
  }
  return all;
};

/**
 * Columns: Type, Date, Time, Name, Amount, Category Group, Category, Account, Notes.
 * All or nothing: if a row is invalid the API answers 400 CSV_INVALID_ROWS with every error.
 */
export const uploadTransactionsCsv = async (file: File | Blob, filename: string): Promise<CsvUploadResponse> => {
  const formData = new FormData();
  formData.append("file", file, filename);
  // No Content-Type header: the browser sets multipart/form-data with its boundary.
  const res = await api.post<CsvUploadResponse>("/transactions/upload-csv", formData);
  return res.data;
};
