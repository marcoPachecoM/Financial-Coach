import { useState } from "react";
import { INITIAL_CHAT_MESSAGES, INITIAL_INSIGHTS } from "../data/mockInsights";
import type { ChatMessage, Insight } from "../types/copilot";
import type { Transaction } from "../types/transaction";
import type { BudgetUsage } from "../types/budget";
import { formatCurrency } from "../utils/formatters";

const now = () => new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
const message = (sender: ChatMessage["sender"], text: string): ChatMessage => ({
  id: `msg-${sender}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
  sender,
  text,
  timestamp: now(),
});

/**
 * Placeholder for the copilot: answers locally from the loaded data until
 * POST /copilot/chat is migrated to the Spring backend.
 */
export const useLocalCopilot = (transactions: Transaction[], budget: BudgetUsage | undefined, periodLabel: string) => {
  const [insights, setInsights] = useState<Insight[]>(INITIAL_INSIGHTS);
  const [messages, setMessages] = useState<ChatMessage[]>(INITIAL_CHAT_MESSAGES);

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
    if (lower.includes("subscription") || lower.includes("audit")) {
      const services = expenses.filter((t) => t.categoryGroup === "Services");
      return `Found ${services.length} transactions in "Services" in this period.`;
    }
    if (lower.includes("analyze") || lower.includes("expense") || lower.includes("spend")) {
      const total = expenses.reduce((sum, t) => sum + Math.abs(t.amount), 0);
      return `In this period (${periodLabel}) you have ${expenses.length} expenses totaling ${formatCurrency(total)}.`;
    }
    return "The AI copilot is not connected to the new backend yet. Try \"analyze my spending\", \"budget\" or \"search: <text>\".";
  };

  const sendMessage = (text: string) => {
    setMessages((prev) => [...prev, message("user", text)]);
    setTimeout(() => say(reply(text)), 400);
  };

  const executeInsight = (id: string) => {
    const insight = insights.find((i) => i.id === id);
    setInsights((prev) => prev.map((i) => (i.id === id ? { ...i, status: "executed" } : i)));
    say(`Rule executed: "${insight?.title}".`);
  };

  const dismissInsight = (id: string) =>
    setInsights((prev) => prev.map((i) => (i.id === id ? { ...i, status: "dismissed" } : i)));

  return { insights, messages, sendMessage, executeInsight, dismissInsight, say };
};
