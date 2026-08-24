import api from "./instance";
import type { TransactionQueryParams, Transaction } from "../types/transaction";
import type { Pagination } from "../types/pagination";

export const getTransactions = async (
  params: TransactionQueryParams,
): Promise<Pagination<Transaction>> => {
  const res = await api.get<Pagination<Transaction>>("/transactions", {
    params,
  });
  return res.data;
};

export interface UploadCsvResponse {
  records_processed: number;
  connection_id: number;
  message: string;
}

export const uploadTransactionsCsv = async (
  file: File | Blob,
  filename = "transactions.csv",
): Promise<UploadCsvResponse> => {
  const formData = new FormData();
  formData.append("file", file, filename);

  const res = await api.post<UploadCsvResponse>("/transactions/upload-csv", formData, {
    headers: {
      "Content-Type": "multipart/form-data",
    },
  });
  return res.data;
};

