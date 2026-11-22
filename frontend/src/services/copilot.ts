import api from "./instance";
import type { ApiChatResponse } from "../types/copilot";

/** The LLM can take a while (tool calls, provider retries); give up after a minute. */
const CHAT_TIMEOUT_MS = 60_000;

/** POST /copilot/chat: the copilot answers with the data of that account (it must be the user's). */
export const sendChatMessage = async (accountId: string, message: string): Promise<ApiChatResponse> => {
  const res = await api.post<ApiChatResponse>(
    "/copilot/chat",
    { message, account_id: accountId },
    { timeout: CHAT_TIMEOUT_MS },
  );
  return res.data;
};
