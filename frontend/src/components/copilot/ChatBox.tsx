import React, { useState } from 'react';
import { SUGGESTION_CHIPS } from '../../data/mockInsights';

interface ChatBoxProps {
  onSendMessage: (text: string) => void;
}

export const ChatBox: React.FC<ChatBoxProps> = ({
  onSendMessage,
}) => {
  const [inputValue, setInputValue] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputValue.trim()) return;
    onSendMessage(inputValue.trim());
    setInputValue('');
  };

  const handleChipClick = (suggestion: string) => {
    onSendMessage(suggestion);
  };

  return (
    <div className="flex flex-col gap-2.5 bg-white p-3 rounded-2xl border border-slate-200 shadow-md">
      {/* Suggestions Chips */}
      <div className="flex flex-wrap gap-1.5">
        {SUGGESTION_CHIPS.map((chip) => (
          <button
            key={chip}
            type="button"
            onClick={() => handleChipClick(chip)}
            className="bg-slate-50 border border-slate-200 text-slate-700 px-2.5 py-1 rounded-full text-[11px] font-medium hover:bg-emerald-50 hover:border-emerald-300 hover:text-emerald-700 transition-all cursor-pointer shadow-2xs"
          >
            {chip}
          </button>
        ))}
      </div>

      {/* Solid Input Form */}
      <form
        onSubmit={handleSubmit}
        className="bg-slate-50 rounded-xl p-1 border border-slate-200 focus-within:bg-white focus-within:border-emerald-500 focus-within:ring-2 focus-within:ring-emerald-100 flex items-center gap-1.5 transition-all"
      >
        <input
          type="text"
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          placeholder="Ask anything about your finances..."
          className="flex-1 bg-transparent border-0 focus:outline-hidden text-xs text-slate-800 placeholder:text-slate-400 px-2.5 py-1.5 font-sans"
        />

        <button
          type="button"
          onClick={() => {
            if (inputValue.trim()) {
              onSendMessage(`Search: ${inputValue.trim()}`);
              setInputValue('');
            }
          }}
          title="Search transactions"
          className="w-7 h-7 rounded-lg bg-slate-200/70 hover:bg-slate-200 flex items-center justify-center text-slate-600 transition-colors cursor-pointer shrink-0"
        >
          <span className="material-symbols-outlined !text-[15px]">search</span>
        </button>

        <button
          type="submit"
          disabled={!inputValue.trim()}
          title="Send prompt"
          className="w-7 h-7 rounded-lg bg-slate-900 text-white flex items-center justify-center hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
        >
          <span className="material-symbols-outlined !text-[15px]">send</span>
        </button>
      </form>
    </div>
  );
};
