import React, { useId, useState } from 'react';

interface TagInputProps {
  id?: string;
  tags: string[];
  onChange: (tags: string[]) => void;
  /** Tags already in use, offered while typing. */
  suggestions?: string[];
  /** Same limits as the API (TagService). */
  max?: number;
}

const MAX_LENGTH = 40;

/** Same cleaning as the API: spaces trimmed and collapsed, a leading "#" removed. */
const clean = (value: string) => value.trim().replace(/\s+/g, ' ').replace(/^#\s*/, '');

/**
 * Tags as removable chips, plus a text box: Enter or "," adds what was typed, Backspace on an empty
 * box removes the last one. Repeats are ignored regardless of case ("Work" = "work").
 */
export const TagInput: React.FC<TagInputProps> = ({ id, tags, onChange, suggestions = [], max = 10 }) => {
  const [draft, setDraft] = useState('');
  const [hint, setHint] = useState<string | null>(null);
  const listId = useId();
  const has = (name: string) => tags.some((t) => t.toLowerCase() === name.toLowerCase());

  const add = (raw: string) => {
    const name = clean(raw);
    setDraft('');
    if (!name) return;
    if (name.includes(';')) return setHint('Tags cannot contain ";".');
    if (name.length > MAX_LENGTH) return setHint(`At most ${MAX_LENGTH} characters per tag.`);
    if (has(name)) return setHint(null);
    if (tags.length >= max) return setHint(`At most ${max} tags.`);
    setHint(null);
    onChange([...tags, name]);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault(); // Enter must not submit the form
      add(draft);
    } else if (e.key === 'Backspace' && draft === '' && tags.length > 0) {
      onChange(tags.slice(0, -1));
    }
  };

  const offered = suggestions.filter((s) => !has(s));

  return (
    <div>
      <div className="flex flex-wrap items-center gap-1.5 w-full px-2 py-1.5 rounded-xl border border-slate-200 focus-within:border-emerald-500 focus-within:ring-1 focus-within:ring-emerald-500 bg-white">
        {tags.map((tag) => (
          <span
            key={tag}
            className="inline-flex items-center gap-1 pl-2 pr-1 py-0.5 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-800 text-xs font-medium"
          >
            #{tag}
            <button
              type="button"
              onClick={() => onChange(tags.filter((t) => t !== tag))}
              aria-label={`Remove tag ${tag}`}
              className="text-emerald-600 hover:text-emerald-900 cursor-pointer leading-none"
            >
              <span aria-hidden="true" className="material-symbols-outlined !text-[14px]">close</span>
            </button>
          </span>
        ))}
        <input
          id={id}
          value={draft}
          list={offered.length > 0 ? listId : undefined}
          onChange={(e) => {
            // Picking a suggestion (or pasting "a, b") fills the box: add at the comma.
            const value = e.target.value;
            if (value.includes(',')) {
              value.split(',').forEach(add);
            } else {
              setDraft(value);
            }
          }}
          onKeyDown={handleKeyDown}
          onBlur={() => add(draft)}
          maxLength={MAX_LENGTH + 1}
          placeholder={tags.length === 0 ? 'e.g. trip-cancun, work' : ''}
          className="flex-1 min-w-[8rem] text-sm px-1 py-0.5 outline-hidden bg-transparent"
        />
        {offered.length > 0 && (
          <datalist id={listId}>
            {offered.map((s) => (
              <option key={s} value={s} />
            ))}
          </datalist>
        )}
      </div>
      <p className={`text-[11px] mt-1 ${hint ? 'text-rose-600' : 'text-slate-400'}`} role={hint ? 'alert' : undefined}>
        {hint ?? 'Press Enter or comma to add. Tags also work as search words.'}
      </p>
    </div>
  );
};
