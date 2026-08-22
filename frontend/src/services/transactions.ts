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
