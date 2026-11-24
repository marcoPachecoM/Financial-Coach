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
  tags: tx.tags ?? [],
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
  /** Tag names; new ones are created by the API. */
  tags?: string[];
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
    ...(input.tags?.length ? { tags: input.tags } : {}),
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

/** PATCH with every field of the form; empty category/notes are cleared. Recurrence is not editable here. */
export const updateTransaction = async (id: string, input: NewTransaction): Promise<Transaction> => {
  const res = await api.patch<ApiTransaction>(`/transactions/${id}`, {
    type: input.type,
    datetime: input.datetime,
    name: input.name,
    amount: signedAmount(input.type, input.amount),
    account_id: input.accountId,
    ...(input.categoryGroup ? { category_group: input.categoryGroup } : {}),
    category: input.category?.trim() || null,
    notes: input.notes?.trim() || null,
    tags: input.tags ?? [],
  });
  return toTransaction(res.data);
};

/** Soft delete: an occurrence of a recurring transaction is not created again. */
export const deleteTransaction = async (id: string): Promise<void> => {
  await api.delete(`/transactions/${id}`);
};

export interface TransactionPageQuery {
  accountId: string;
  /** Inclusive dates "YYYY-MM-DD"; null means no limit. */
  startDate: string | null;
  endDate: string | null;
  /** Contained in the name, category, notes or a tag. */
  text: string | null;
  /** Exact tag name. */
  tag: string | null;
  categoryGroup: string | null;
  /** Bounds of the absolute amount, inclusive. */
  minAmount: number | null;
  maxAmount: number | null;
  /** From 1. */
  page: number;
  size: number;
}

export interface TransactionPage {
  transactions: Transaction[];
  page: number;
  totalElements: number;
  totalPages: number;
}

/** GET /transactions with every filter applied by the server, newest first: one page. */
export const getTransactionPage = async (query: TransactionPageQuery): Promise<TransactionPage> => {
  const params: Record<string, string | number> = { account_id: query.accountId, page: query.page, size: query.size };
  if (query.startDate) params.start_date = query.startDate;
  if (query.endDate) params.end_date = query.endDate;
  if (query.text) params.q = query.text;
  if (query.categoryGroup) params.category_group = query.categoryGroup;
  if (query.tag) params.tag = query.tag;
  if (query.minAmount !== null) params.min_amount = query.minAmount.toFixed(2);
  if (query.maxAmount !== null) params.max_amount = query.maxAmount.toFixed(2);
  const res = await api.get<ApiTransactionPage>("/transactions", { params });
  return {
    transactions: res.data.content.map(toTransaction),
    page: res.data.page,
    totalElements: res.data.total_elements,
    totalPages: res.data.total_pages,
  };
};

export interface CsvExportOptions {
  /** null exports every account of the user. */
  accountId: string | null;
  /** yyyy-MM-dd, inclusive; null for no limit. */
  startDate: string | null;
  endDate: string | null;
}

/** "attachment; filename="x.csv"" → "x.csv". */
const filenameFrom = (contentDisposition: unknown): string | null => {
  const match = typeof contentDisposition === "string" ? /filename="([^"]+)"/.exec(contentDisposition) : null;
  return match ? match[1] : null;
};

/**
 * GET /transactions/export: the transactions as a CSV file in the import format, so it can be
 * imported again. Returns the file, the name the server chose for it and its number of rows
 * (X-Row-Count; null if a proxy dropped the header).
 */
export const exportTransactionsCsv = async ({ accountId, startDate, endDate }: CsvExportOptions) => {
  const params: Record<string, string> = {};
  if (accountId) params.account_id = accountId;
  if (startDate) params.start_date = startDate;
  if (endDate) params.end_date = endDate;
  const res = await api.get<Blob>("/transactions/export", { params, responseType: "blob", timeout: 60_000 });
  const rowCount = Number.parseInt(String(res.headers["x-row-count"] ?? ""), 10);
  return {
    blob: res.data,
    filename: filenameFrom(res.headers["content-disposition"]) ?? "transactions.csv",
    rows: Number.isNaN(rowCount) ? null : rowCount,
  };
};

/** Makes the browser save a file it already has in memory. */
export const saveFile = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // After the click the browser has the file; the URL is no longer needed.
  setTimeout(() => URL.revokeObjectURL(url), 0);
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
