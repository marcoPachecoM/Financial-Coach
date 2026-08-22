import type { Connection } from "../types/connection";
import api from "./instance";

export const getConnections = async (): Promise<Connection[]> => {
  const res = await api.get<Connection[]>("/connections");
  return res.data;
};
