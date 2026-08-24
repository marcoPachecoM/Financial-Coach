import api from "./instance";
import type { Connection } from "../types/connection";

export const getConnections = async (): Promise<Connection[]> => {
  const res = await api.get<Connection[]>("/connections");
  return res.data;
};
