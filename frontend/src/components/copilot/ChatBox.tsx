import React, { useState } from 'react';
import { SUGGESTION_CHIPS } from '../../data/chatSuggestions';

interface ChatBoxProps {
  onSendMessage: (text: string) => void;
  /** While the copilot is answering: the text can be typed but not sent. */
  disabled?: boolean;
  /** Shown instead of Send while the copilot is answering: cancels the answer. */
  onStop?: () => void;
}

export const ChatBox: React.FC<ChatBoxProps> = ({
  onSendMessage,
  disabled = false,
  onStop,
}) => {
  const [inputValue, setInputValue] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (disabled || !inputValue.trim()) return;
    onSendMessage(inputValue.trim());
    setInputValue('');
  };

  const handleChipClick = (suggestion: string) => {
    if (disabled) return;
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
            disabled={disabled}
            className="bg-slate-50 border border-slate-200 text-slate-700 px-2.5 py-1 rounded-full text-[11px] font-medium hover:bg-emerald-50 hover:border-emerald-300 hover:text-emerald-700 transition-all cursor-pointer shadow-2xs disabled:opacity-50 disabled:cursor-not-allowed"
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
          maxLength={1000}
          aria-label="Message to the copilot"
          placeholder="Ask anything about your finances..."
          className="flex-1 bg-transparent border-0 focus:outline-hidden text-xs text-slate-800 placeholder:text-slate-400 px-2.5 py-1.5 font-sans"
        />

        {disabled && onStop ? (
          <button
            type="button"
            onClick={onStop}
            title="Stop answering"
            aria-label="Stop answering"
            className="w-7 h-7 rounded-lg bg-slate-900 text-white flex items-center justify-center hover:bg-rose-600 transition-colors cursor-pointer shrink-0"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[15px]">stop</span>
          </button>
        ) : (
          <button
            type="submit"
            disabled={disabled || !inputValue.trim()}
            title="Send prompt"
            aria-label="Send prompt"
            className="w-7 h-7 rounded-lg bg-slate-900 text-white flex items-center justify-center hover:bg-slate-800 disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer shrink-0"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[15px]">send</span>
          </button>
        )}
      </form>
    </div>
  );
};
