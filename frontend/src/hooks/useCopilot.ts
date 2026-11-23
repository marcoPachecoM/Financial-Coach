import { useEffect, useMemo, useRef, useState } from "react";
import { isAxiosError } from "axios";
import { INITIAL_CHAT_MESSAGES } from "../data/mockInsights";
import { streamChatMessage } from "../services/copilot";
import { getErrorMessage } from "../services/errors";
import { makeRecurring, stopRecurring, updateRecurring } from "../services/recurring";
import type { AlertAction, ApiAlert, RecurrenceFrequency } from "../types/api";
import type { ChatMessage } from "../types/copilot";
import { FREQUENCY_LABELS } from "../types/recurring";
import { toInsight } from "../utils/alertMessages";
import { formatLocalDateToISO } from "../utils/dateHelper";
import { formatCurrency, formatFullDate } from "../utils/formatters";

const DISMISSED_KEY = "financial-coach.dismissed-alerts";
/** Keeps localStorage small; the oldest dismissals are forgotten first. */
const MAX_DISMISSED = 300;
/** Same limit as ChatRequestRecord (@Size max = 1000). */
const MAX_MESSAGE_LENGTH = 1000;

const now = () => new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
const message = (sender: ChatMessage["sender"], text: string, tokens?: number): ChatMessage => ({
  id: `msg-${sender}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
  sender,
  text,
  timestamp: now(),
  tokens,
});

/** Dismissed alerts are a per-browser convenience: losing them only shows the alert again. */
const loadDismissed = (): string[] => {
  try {
    const stored = JSON.parse(localStorage.getItem(DISMISSED_KEY) ?? "[]");
    return Array.isArray(stored) ? stored.filter((v): v is string => typeof v === "string") : [];
  } catch {
    return [];
  }
};

const saveDismissed = (keys: string[]) => {
  try {
    localStorage.setItem(DISMISSED_KEY, JSON.stringify(keys.slice(-MAX_DISMISSED)));
  } catch {
    // Private mode or storage full: dismissals last until the page is reloaded.
  }
};

/** Chat errors in the copilot's voice; a timeout is not "the server is down". */
const chatErrorMessage = (error: unknown): string => {
  const timedOut =
    (isAxiosError(error) && error.code === "ECONNABORTED") ||
    (error instanceof DOMException && error.name === "AbortError");
  if (timedOut) {
    return "The copilot is taking too long to answer. Please try again in a moment.";
  }
  return `I couldn't answer that: ${getErrorMessage(error)}`;
};

interface Chat {
  /** conversation_id sent to the server, which keeps the history per conversation. */
  id: string;
  messages: ChatMessage[];
}

const NO_ACCOUNT = "none";
const newChat = (): Chat => ({ id: crypto.randomUUID(), messages: INITIAL_CHAT_MESSAGES });
const EMPTY_CHAT: Chat = { id: "", messages: INITIAL_CHAT_MESSAGES };

interface CopilotInput {
  accountId: string | null;
  alerts: ApiAlert[];
  /** Called after an action changed data, so the dashboard reloads. */
  onDataChanged: () => void;
}

/**
 * The copilot panel: real alerts from the API (with their actions) and a chat answered by
 * POST /copilot/chat/stream, with one conversation per account.
 */
export const useCopilot = ({ accountId, alerts, onDataChanged }: CopilotInput) => {
  /**
   * One chat per account, kept while the page is open: switching accounts and coming back shows
   * the same messages, and the server still remembers that conversation (its id). Chats about one
   * account never mix with another's. Not saved to localStorage: they hold financial data.
   */
  const chatKey = accountId ?? NO_ACCOUNT;
  const [chats, setChats] = useState<Record<string, Chat>>(() => ({ [chatKey]: newChat() }));
  // EMPTY_CHAT only in the render that switches to a new account; React discards it and renders
  // again with the chat created below.
  const chat = chats[chatKey] ?? EMPTY_CHAT;
  const messages = chat.messages;
  /** Changes the chat of `key` (by default, the one on screen). */
  const updateChat = (change: (prev: ChatMessage[]) => ChatMessage[], key = chatKey) =>
    setChats((cs) => (cs[key] ? { ...cs, [key]: { ...cs[key], messages: change(cs[key].messages) } } : cs));
  const [dismissed, setDismissed] = useState<string[]>(loadDismissed);
  const [busyInsightId, setBusyInsightId] = useState<string | null>(null);
  /** Waiting for the first piece of the answer. */
  const [thinking, setThinking] = useState(false);
  /** From sending until the answer is complete: no new message meanwhile. */
  const [busy, setBusy] = useState(false);
  const [chatAccount, setChatAccount] = useState(accountId);
  /** The answer being streamed. Also guards against double sends before `busy` re-renders. */
  const streaming = useRef<AbortController | null>(null);

  // Leaving an account (or unmounting) cancels the answer still streaming: the server stops
  // generating and spending tokens on something nobody is looking at.
  useEffect(() => () => streaming.current?.abort(), [accountId]);

  const dismissKey = (alertId: string) => `${accountId}|${alertId}`;
  const insights = useMemo(
    () => alerts.filter((alert) => !dismissed.includes(`${accountId}|${alert.id}`)).map(toInsight),
    [alerts, dismissed, accountId],
  );

  // Another account: show its chat, creating it the first time (state adjusted during render, as
  // React recommends, instead of an effect that would first paint the previous account's messages).
  if (accountId !== chatAccount) {
    setChatAccount(accountId);
    if (!chats[chatKey]) setChats((cs) => (cs[chatKey] ? cs : { ...cs, [chatKey]: newChat() }));
    setThinking(false);
    setBusy(false);
  }

  const say = (text: string) => updateChat((prev) => [...prev, message("assistant", text)]);

  const sendMessage = async (text: string) => {
    if (streaming.current && !streaming.current.signal.aborted) return;
    updateChat((prev) => [...prev, message("user", text)]);

    if (!accountId) {
      say("Select an account first, so I know which data to look at.");
      return;
    }

    const controller = new AbortController();
    streaming.current = controller;
    const key = chatKey;
    const conversationId = chat.id;
    setThinking(true);
    setBusy(true);
    // Always writes to the chat it was asked in, even if another account is on screen by then.
    const inThisChat = (change: (prev: ChatMessage[]) => ChatMessage[]) => updateChat(change, key);
    // The answer bubble appears with the first piece and grows with each one.
    const answer = message("assistant", "");
    let started = false;
    const update = (change: (m: ChatMessage) => ChatMessage) =>
      inThisChat((prev) => prev.map((m) => (m.id === answer.id ? change(m) : m)));

    try {
      const done = await streamChatMessage(
        accountId,
        conversationId,
        text.slice(0, MAX_MESSAGE_LENGTH),
        (delta) => {
          if (!started) {
            started = true;
            setThinking(false);
            inThisChat((prev) => [...prev, { ...answer, text: delta }]);
          } else {
            update((m) => ({ ...m, text: m.text + delta }));
          }
        },
        controller.signal,
      );
      update((m) => ({ ...m, tokens: done.usage?.total_tokens }));
    } catch (error) {
      // Cancelled because the user switched accounts: say so in that chat, seen when coming back.
      const text = controller.signal.aborted
        ? "I stopped answering because you switched accounts. Ask again if you still need it."
        : chatErrorMessage(error);
      if (started) {
        update((m) => ({ ...m, text: `${m.text}\n\n(${text})` }));
      } else {
        inThisChat((prev) => [...prev, message("assistant", text)]);
      }
    } finally {
      // A newer message may already be streaming; its state is not ours to reset.
      if (streaming.current === controller) {
        streaming.current = null;
        setThinking(false);
        setBusy(false);
      }
    }
  };

  const dismissInsight = (alertId: string) => {
    const next = [...dismissed.filter((k) => k !== dismissKey(alertId)), dismissKey(alertId)];
    setDismissed(next);
    saveDismissed(next);
  };

  /** Runs the action the API offered for an alert (see AlertAction in docs/openapi.yaml). */
  const runAction = async (alertId: string, action: AlertAction) => {
    const alert = alerts.find((a) => a.id === alertId);
    if (!alert) return;
    const d = alert.data;
    const name = String(d.name ?? "");

    setBusyInsightId(alertId);
    try {
      if (action === "CREATE_RECURRING") {
        const frequency = d.frequency as RecurrenceFrequency;
        const rule = await makeRecurring(String(d.transaction_id), frequency);
        say(
          `${name} is now recurring (${FREQUENCY_LABELS[frequency].toLowerCase()}).` +
            (rule.nextDueDate ? ` The next charge will be added on ${formatFullDate(rule.nextDueDate)}.` : ""),
        );
      } else if (action === "UPDATE_RECURRING_AMOUNT") {
        const rule = await updateRecurring(String(d.recurring_transaction_id), { amount: String(d.charged_amount) });
        say(`${name} will be added at ${formatCurrency(rule.amount)} from now on.`);
      } else if (action === "STOP_RECURRING") {
        await stopRecurring(String(d.recurring_transaction_id), formatLocalDateToISO(new Date()));
        say(`${name} will no longer be added automatically.`);
      }
      onDataChanged();
    } catch (error) {
      say(`I couldn't do that: ${getErrorMessage(error)}`);
    } finally {
      setBusyInsightId(null);
    }
  };

  return { insights, busyInsightId, messages, thinking, busy, sendMessage, runAction, dismissInsight, say };
};
