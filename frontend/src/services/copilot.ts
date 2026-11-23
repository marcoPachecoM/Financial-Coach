import api from "./instance";
import type { ApiChatResponse } from "../types/copilot";

/** The LLM can take a while (tool calls, provider retries); give up after a minute. */
const CHAT_TIMEOUT_MS = 60_000;

/**
 * POST /copilot/chat: the copilot answers with the data of that account (it must be the user's).
 * Messages with the same conversationId share history, so follow-up questions work.
 */
export const sendChatMessage = async (
  accountId: string,
  conversationId: string,
  message: string,
): Promise<ApiChatResponse> => {
  const res = await api.post<ApiChatResponse>(
    "/copilot/chat",
    { message, account_id: accountId, conversation_id: conversationId },
    { timeout: CHAT_TIMEOUT_MS },
  );
  return res.data;
};
