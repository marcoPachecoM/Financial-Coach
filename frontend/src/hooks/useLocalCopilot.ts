import { useMemo, useState } from "react";
import { INITIAL_CHAT_MESSAGES } from "../data/mockInsights";
import { getErrorMessage } from "../services/errors";
import { makeRecurring, stopRecurring, updateRecurring } from "../services/recurring";
import type { AlertAction, ApiAlert, RecurrenceFrequency } from "../types/api";
import type { ChatMessage } from "../types/copilot";
import type { Transaction } from "../types/transaction";
import type { BudgetUsage } from "../types/budget";
import { FREQUENCY_LABELS, type RecurringTransaction } from "../types/recurring";
import { toInsight } from "../utils/alertMessages";
import { formatLocalDateToISO } from "../utils/dateHelper";
import { formatCurrency, formatFullDate } from "../utils/formatters";

const DISMISSED_KEY = "financial-coach.dismissed-alerts";
/** Keeps localStorage small; the oldest dismissals are forgotten first. */
const MAX_DISMISSED = 300;

const now = () => new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
const message = (sender: ChatMessage["sender"], text: string): ChatMessage => ({
  id: `msg-${sender}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
  sender,
  text,
  timestamp: now(),
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

interface LocalCopilotInput {
  accountId: string | null;
  transactions: Transaction[];
  budget: BudgetUsage | undefined;
  recurring: RecurringTransaction[];
  alerts: ApiAlert[];
  periodLabel: string;
  /** Called after an action changed data, so the dashboard reloads. */
  onDataChanged: () => void;
}

/**
 * The copilot panel: real alerts from the API (with their actions) plus a chat that still
 * answers locally from the loaded data until POST /copilot/chat is migrated to Spring.
 */
export const useLocalCopilot = ({
  accountId,
  transactions,
  budget,
  recurring,
  alerts,
  periodLabel,
  onDataChanged,
}: LocalCopilotInput) => {
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_CHAT_MESSAGES);
  const [dismissed, setDismissed] = useState<string[]>(loadDismissed);
  const [busyInsightId, setBusyInsightId] = useState<string | null>(null);

  const dismissKey = (alertId: string) => `${accountId}|${alertId}`;
  const insights = useMemo(
    () => alerts.filter((alert) => !dismissed.includes(`${accountId}|${alert.id}`)).map(toInsight),
    [alerts, dismissed, accountId],
  );

  const say = (text: string) => setMessages((prev) => [...prev, message("assistant", text)]);

  const reply = (text: string): string => {
    const lower = text.toLowerCase();
    const expenses = transactions.filter((t) => t.type === "EXPENSE");

    if (lower.startsWith("search:")) {
      const query = text.replace(/^search:\s*/i, "").toLowerCase();
      const matches = transactions.filter(
        (t) => [t.name, t.categoryGroup, t.category].some((v) => v?.toLowerCase().includes(query)),
      );
      return `Search found ${matches.length} matching transactions for "${query}".`;
    }
    if (lower.includes("budget")) {
      if (!budget || budget.budget === null) return "This account has no monthly budget yet.";
      return `You have spent ${formatCurrency(budget.spent)} of your ${formatCurrency(budget.budget)} budget this month (${budget.percentageUsed ?? 0}%).`;
    }
    if (lower.includes("subscription") || lower.includes("recurring") || lower.includes("audit")) {
      const active = recurring.filter((r) => r.nextDueDate !== null && r.type === "EXPENSE");
      if (active.length === 0) return "You have no active recurring expenses. Mark a transaction as recurring to track it.";
      const monthly = active
        .filter((r) => r.frequency === "MONTHLY")
        .reduce((sum, r) => sum + r.amount, 0);
      const list = active
        .map((r) => `${r.name} (${formatCurrency(r.amount)}, next ${formatFullDate(r.nextDueDate!)})`)
        .join("; ");
      return `You have ${active.length} active recurring expense(s): ${list}. Monthly ones add up to ${formatCurrency(monthly)}.`;
    }
    if (lower.includes("analyze") || lower.includes("expense") || lower.includes("spend")) {
      const total = expenses.reduce((sum, t) => sum + Math.abs(t.amount), 0);
      return `In this period (${periodLabel}) you have ${expenses.length} expenses totaling ${formatCurrency(total)}.`;
    }
    return "The AI copilot is not connected to the new backend yet. Try \"analyze my spending\", \"budget\", \"subscription audit\" or \"search: <text>\".";
  };

  const sendMessage = (text: string) => {
    setMessages((prev) => [...prev, message("user", text)]);
    setTimeout(() => say(reply(text)), 400);
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

  return { insights, busyInsightId, messages, sendMessage, runAction, dismissInsight, say };
};
