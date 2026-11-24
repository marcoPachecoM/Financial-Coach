import React, { useState } from 'react';
import { deleteGoal } from '../../services/goals';
import { getErrorMessage } from '../../services/errors';
import type { Goal } from '../../types/goal';
import { AddMoneyModal } from './AddMoneyModal';
import { GoalCard } from './GoalCard';
import { GoalDetailPanel } from './GoalDetailPanel';
import { GoalFormModal } from './GoalFormModal';

interface GoalsViewProps {
  /** The user's goals (not tied to an account), the ones needing attention first. */
  goals: Goal[] | undefined;
  loading: boolean;
  error: string | null;
  /** Reloads the goals after any change (cards, panel and sidebar badge). */
  onChanged: () => void;
  /** Short confirmation in the copilot chat. */
  onSay: (message: string) => void;
}

/** Savings goals: their own view, so the dashboard stays about this month. */
export const GoalsView: React.FC<GoalsViewProps> = ({ goals, loading, error, onChanged, onSay }) => {
  const [form, setForm] = useState<{ open: boolean; goal: Goal | null }>({ open: false, goal: null });
  const [addingTo, setAddingTo] = useState<Goal | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const [version, setVersion] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);

  // The panel follows the reloaded list, so its numbers update after every change.
  const openGoal = goals?.find((g) => g.id === openId) ?? null;

  const changed = () => {
    setVersion((v) => v + 1);
    onChanged();
  };

  const remove = async (goal: Goal) => {
    if (!window.confirm(`Delete "${goal.name}" and its history? This cannot be undone.`)) return;
    setActionError(null);
    try {
      await deleteGoal(goal.id);
      if (openId === goal.id) setOpenId(null);
      onSay(`Deleted the goal ${goal.name}.`);
      changed();
    } catch (err: unknown) {
      setActionError(getErrorMessage(err, 'Could not delete the goal.'));
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold text-slate-900 tracking-tight">Goals</h2>
          <p className="text-sm text-slate-500 mt-0.5">What you're saving for, and whether you'll get there in time.</p>
        </div>
        <button
          onClick={() => setForm({ open: true, goal: null })}
          className="inline-flex items-center gap-1.5 bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-xl text-sm font-semibold cursor-pointer"
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[18px]">add</span>
          New goal
        </button>
      </div>

      {(error || actionError) && (
        <p className="p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm" role="alert">
          {actionError ?? error}
        </p>
      )}

      {!goals ? (
        <p className="text-sm text-slate-400">{loading ? 'Loading goals...' : ''}</p>
      ) : goals.length === 0 ? (
        <div className="bg-white rounded-2xl p-10 border border-slate-200/80 card-shadow text-center">
          <span className="material-symbols-outlined !text-[40px] text-slate-300">flag</span>
          <p className="mt-2 font-semibold text-slate-900">No goals yet</p>
          <p className="text-sm text-slate-500 mt-1 max-w-sm mx-auto">
            An emergency fund, a trip, a new laptop… Set a target and an optional date, add money as you save, and
            we'll tell you if you're on track.
          </p>
          <button
            onClick={() => setForm({ open: true, goal: null })}
            className="mt-4 bg-emerald-600 hover:bg-emerald-700 text-white px-4 py-2 rounded-xl text-sm font-semibold cursor-pointer"
          >
            Create your first goal
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {goals.map((goal) => (
            <GoalCard
              key={goal.id}
              goal={goal}
              onOpen={() => setOpenId(goal.id)}
              onAddMoney={() => setAddingTo(goal)}
              onEdit={() => setForm({ open: true, goal })}
              onDelete={() => remove(goal)}
            />
          ))}
        </div>
      )}

      {openGoal && (
        <GoalDetailPanel
          goal={openGoal}
          version={version}
          onClose={() => setOpenId(null)}
          onAddMoney={() => setAddingTo(openGoal)}
          onChanged={changed}
        />
      )}

      <GoalFormModal
        key={form.open ? (form.goal?.id ?? 'new-goal') : 'closed'}
        isOpen={form.open}
        goal={form.goal}
        onClose={() => setForm({ open: false, goal: null })}
        onSaved={(goal, created) => {
          onSay(created ? `Created the goal ${goal.name}.` : `Saved the goal ${goal.name}.`);
          changed();
        }}
      />

      <AddMoneyModal
        key={addingTo?.id ?? 'closed'}
        goal={addingTo}
        onClose={() => setAddingTo(null)}
        onSaved={(goal) => {
          onSay(
            goal.status === 'COMPLETED'
              ? `🎉 You reached your goal ${goal.name}!`
              : `Saved. ${goal.name} is at ${Math.round(goal.percentage)}%.`,
          );
          changed();
        }}
      />
    </div>
  );
};
