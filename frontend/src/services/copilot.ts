import api, { refreshAccessTokenOrExpire } from "./instance";
import { authStorage } from "../auth/storage";
import type { ProblemDetail } from "../types/api";
import type { ApiChatResponse, ApiChatStreamDone } from "../types/copilot";

/** The LLM can take a while (tool calls, provider retries); give up after a minute. */
const CHAT_TIMEOUT_MS = 60_000;
/**
 * A streamed answer gives up only after this long without receiving anything, so a long answer
 * that keeps arriving is never cut. Before the first piece the model may be calling tools (each
 * call up to 20 s on the server), hence the margin. The server ends any stream after 90 s.
 */
const STREAM_IDLE_TIMEOUT_MS = 45_000;

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

/** A failed streamed answer; `message` is the server's detail, ready to show. */
export class ChatStreamError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "ChatStreamError";
    this.code = code;
  }
}

/**
 * POST /copilot/chat/stream: the same answer as sendChatMessage, delivered as it is written.
 * `onDelta` receives each piece of text (append them); the promise resolves with the usage when the
 * answer is complete. Uses fetch because EventSource cannot send a POST body or the token header.
 * Aborting `signal` cancels the request (the server stops generating) and rejects with an AbortError.
 */
export const streamChatMessage = async (
  accountId: string,
  conversationId: string,
  message: string,
  onDelta: (text: string) => void,
  signal?: AbortSignal,
): Promise<ApiChatStreamDone> => {
  const abort = new AbortController();
  let timeout = setTimeout(() => abort.abort(), STREAM_IDLE_TIMEOUT_MS);
  const stillAlive = () => {
    clearTimeout(timeout);
    timeout = setTimeout(() => abort.abort(), STREAM_IDLE_TIMEOUT_MS);
  };
  const cancel = () => abort.abort();
  if (signal?.aborted) cancel();
  signal?.addEventListener("abort", cancel, { once: true });
  try {
    let response = await postStream(accountId, conversationId, message, authStorage.getAccessToken(), abort.signal);
    if (response.status === 401 && (await problemOf(response.clone()))?.code === "INVALID_TOKEN") {
      // Expired access token: refresh once (same flow as axios requests) and retry.
      const token = await refreshAccessTokenOrExpire();
      response = await postStream(accountId, conversationId, message, token, abort.signal);
    }
    if (!response.ok || !response.body) {
      const problem = await problemOf(response);
      throw new ChatStreamError(problem?.code ?? `HTTP_${response.status}`, problem?.detail ?? "The copilot request failed.");
    }
    return await readEvents(response.body, onDelta, stillAlive);
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener("abort", cancel);
  }
};

const postStream = (
  accountId: string,
  conversationId: string,
  message: string,
  token: string | null,
  signal: AbortSignal,
): Promise<Response> =>
  fetch(`${import.meta.env.VITE_API_URL}/copilot/chat/stream`, {
    method: "POST",
    signal,
    headers: {
      "Content-Type": "application/json",
      // Errors before the stream starts (404, 429, validation) come as Problem Details.
      Accept: "text/event-stream, application/problem+json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message, account_id: accountId, conversation_id: conversationId }),
  });

const problemOf = async (response: Response): Promise<Partial<ProblemDetail> | null> => {
  try {
    return (await response.json()) as Partial<ProblemDetail>;
  } catch {
    return null;
  }
};

/**
 * Reads Server-Sent Events: blocks separated by a blank line, with "event:" and "data:" lines.
 * Network chunks can split a block anywhere, so incomplete text stays in the buffer.
 */
const readEvents = async (
  body: ReadableStream<Uint8Array>,
  onDelta: (text: string) => void,
  onData: () => void,
): Promise<ApiChatStreamDone> => {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    onData();
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, "\n");

    let end: number;
    while ((end = buffer.indexOf("\n\n")) >= 0) {
      const block = buffer.slice(0, end);
      buffer = buffer.slice(end + 2);

      let event = "message";
      const data: string[] = [];
      for (const line of block.split("\n")) {
        if (line.startsWith("event:")) event = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
      }
      if (data.length === 0) continue;
      const payload = JSON.parse(data.join("\n"));

      if (event === "delta") onDelta(payload.text as string);
      else if (event === "done") return payload as ApiChatStreamDone;
      else if (event === "error") throw new ChatStreamError(payload.code, payload.detail);
    }
  }
  throw new ChatStreamError("STREAM_INTERRUPTED", "The answer was cut off. Please try again.");
};
