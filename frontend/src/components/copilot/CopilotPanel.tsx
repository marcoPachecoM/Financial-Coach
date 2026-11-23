import React from 'react';
import type { AlertAction } from '../../types/api';
import type { Insight, ChatMessage } from '../../types/copilot';
import { InsightCard } from './InsightCard';
import { ChatBox } from './ChatBox';

interface CopilotPanelProps {
  insights: Insight[];
  /** Insight whose action is running, if any. */
  busyInsightId: string | null;
  messages: ChatMessage[];
  /** Waiting for the copilot's answer. */
  thinking: boolean;
  onInsightAction: (id: string, action: AlertAction) => void;
  onDismissInsight: (id: string) => void;
  onSendMessage: (text: string) => void;
  /** Clears the chat and starts a conversation without history. */
  onNewChat: () => void;
}

export const CopilotPanel: React.FC<CopilotPanelProps> = ({
  insights,
  busyInsightId,
  messages,
  thinking,
  onInsightAction,
  onDismissInsight,
  onSendMessage,
  onNewChat,
}) => {
  return (
    <aside className="w-full lg:w-92 lg:sticky lg:top-20 lg:self-start lg:h-[calc(100vh-6.5rem)] flex flex-col justify-between gap-3">
      {/* 1. Header (Fixed at top of panel) */}
      <div className="bg-white rounded-2xl p-4 border border-slate-200/80 card-shadow flex items-center justify-between shrink-0 shadow-xs">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center text-white shadow-xs">
            <span className="material-symbols-outlined !text-[18px]">smart_toy</span>
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900 font-sans leading-none flex items-center gap-1.5">
              Mush Copilot
              <span className="w-2 h-2 rounded-full bg-emerald-500 ring-2 ring-emerald-100 animate-pulse" />
            </h3>
            <span className="text-[10px] text-slate-400 font-mono">Autonomous Advisor</span>
          </div>
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onNewChat}
            disabled={thinking}
            title="New chat"
            className="w-7 h-7 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[18px]">edit_square</span>
          </button>
          <span className="text-[11px] font-mono text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-0.5 rounded-full font-semibold">
            AI Active
          </span>
        </div>
      </div>

      {/* 2. Scrollable Feed & Insights Area */}
      <div className="flex-1 overflow-y-auto space-y-3 min-h-0 pr-1 py-1">
        {/* Alerts of the account (GET /accounts/{id}/alerts) */}
        {insights.map((insight) => (
          <InsightCard
            key={insight.id}
            insight={insight}
            busy={busyInsightId === insight.id}
            onAction={onInsightAction}
            onDismiss={onDismissInsight}
          />
        ))}

        {/* Chat History Bubbles */}
        {messages.map((msg) => (
          <div
            key={msg.id}
            className={`rounded-2xl p-3.5 text-xs transition-all ${
              msg.sender === 'user'
                ? 'bg-slate-900 text-white ml-6 self-end rounded-br-xs shadow-xs'
                : 'bg-emerald-50/80 border border-emerald-100 text-slate-800 mr-4 self-start rounded-bl-xs'
            }`}
          >
            <div className="flex items-center justify-between mb-1">
              <span className="font-semibold text-[11px] opacity-80">
                {msg.sender === 'user' ? 'You' : 'Mush AI'}
              </span>
              <span className="text-[10px] opacity-60 font-mono">{msg.timestamp}</span>
            </div>
            <p className="leading-relaxed">{msg.text}</p>
          </div>
        ))}

        {thinking && (
          <div className="rounded-2xl p-3.5 text-xs bg-emerald-50/80 border border-emerald-100 text-slate-500 mr-4 rounded-bl-xs flex items-center gap-2">
            <span className="material-symbols-outlined !text-[16px] animate-spin text-emerald-600">progress_activity</span>
            Mush AI is thinking...
          </div>
        )}
      </div>

      {/* 3. Sticky / Fixed Bottom Chat Input with Solid Background */}
      <div className="shrink-0 sticky bottom-0 z-20 bg-[#f7f9fb] pt-1">
        <ChatBox onSendMessage={onSendMessage} disabled={thinking} />
      </div>
    </aside>
  );
};
