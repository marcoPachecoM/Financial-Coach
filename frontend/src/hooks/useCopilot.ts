import { useMemo, useRef, useState } from "react";
import { isAxiosError } from "axios";
import { INITIAL_CHAT_MESSAGES } from "../data/mockInsights";
import { streamChatMessage } from "../services/copilot";
import { getErrorMessage } from "../services/errors";
import { makeRecurring, stopRecurring, updateRecurring } from "../services/recurring";
import type { AlertAction, ApiAlert, RecurrenceFrequency } from "../types/api";
import type { ChatMessage } from "../types/copilot";
import type { Transaction } from "../types/transaction";
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

interface CopilotInput {
  accountId: string | null;
  /** Transactions on screen, for the local "Search:" command. */
  transactions: Transaction[];
  alerts: ApiAlert[];
  /** Called after an action changed data, so the dashboard reloads. */
  onDataChanged: () => void;
}

/**
 * The copilot panel: real alerts from the API (with their actions) and a chat answered by
 * POST /copilot/chat. "Search: <text>" stays local: it only filters the loaded transactions.
 */
export const useCopilot = ({ accountId, transactions, alerts, onDataChanged }: CopilotInput) => {
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_CHAT_MESSAGES);
  const [dismissed, setDismissed] = useState<string[]>(loadDismissed);
  const [busyInsightId, setBusyInsightId] = useState<string | null>(null);
  /** Waiting for the first piece of the answer. */
  const [thinking, setThinking] = useState(false);
  /** From sending until the answer is complete: no new message meanwhile. */
  const [busy, setBusy] = useState(false);
  /** The server keeps the history per conversation; a new id starts a fresh chat. */
  const [conversationId, setConversationId] = useState(() => crypto.randomUUID());
  const [conversationAccount, setConversationAccount] = useState(accountId);
  /** Guards against double sends before the `thinking` state re-renders. */
  const sending = useRef(false);

  const dismissKey = (alertId: string) => `${accountId}|${alertId}`;
  const insights = useMemo(
    () => alerts.filter((alert) => !dismissed.includes(`${accountId}|${alert.id}`)).map(toInsight),
    [alerts, dismissed, accountId],
  );

  const newChat = () => {
    setConversationId(crypto.randomUUID());
    setMessages(INITIAL_CHAT_MESSAGES);
  };

  // Another account means other data: start a new chat (state adjusted during render, as React
  // recommends, instead of an effect that would first paint the old messages).
  if (accountId !== conversationAccount) {
    setConversationAccount(accountId);
    newChat();
  }

  const say = (text: string) => setMessages((prev) => [...prev, message("assistant", text)]);

  const search = (text: string): string => {
    const query = text.replace(/^search:\s*/i, "").toLowerCase();
    const matches = transactions.filter((t) =>
      [t.name, t.categoryGroup, t.category].some((v) => v?.toLowerCase().includes(query)),
    );
    return `Search found ${matches.length} matching transactions for "${query}".`;
  };

  const sendMessage = async (text: string) => {
    if (sending.current) return;
    setMessages((prev) => [...prev, message("user", text)]);

    if (/^search:/i.test(text)) {
      say(search(text));
      return;
    }
    if (!accountId) {
      say("Select an account first, so I know which data to look at.");
      return;
    }

    sending.current = true;
    setThinking(true);
    setBusy(true);
    // The answer bubble appears with the first piece and grows with each one.
    const answer = message("assistant", "");
    let started = false;
    const update = (change: (m: ChatMessage) => ChatMessage) =>
      setMessages((prev) => prev.map((m) => (m.id === answer.id ? change(m) : m)));

    try {
      const done = await streamChatMessage(accountId, conversationId, text.slice(0, MAX_MESSAGE_LENGTH), (delta) => {
        if (!started) {
          started = true;
          setThinking(false);
          setMessages((prev) => [...prev, { ...answer, text: delta }]);
        } else {
          update((m) => ({ ...m, text: m.text + delta }));
        }
      });
      update((m) => ({ ...m, tokens: done.usage?.total_tokens }));
    } catch (error) {
      if (started) {
        update((m) => ({ ...m, text: `${m.text}\n\n(${chatErrorMessage(error)})` }));
      } else {
        say(chatErrorMessage(error));
      }
    } finally {
      sending.current = false;
      setThinking(false);
      setBusy(false);
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

  return { insights, busyInsightId, messages, thinking, busy, sendMessage, newChat, runAction, dismissInsight, say };
};
