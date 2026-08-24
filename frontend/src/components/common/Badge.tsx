import React from 'react';
import type { TransactionStatus } from '../../types/transaction';
import { getCategoryStyle } from '../../utils/categoryHelper';

interface StatusBadgeProps {
  status?: TransactionStatus;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status = 'verified' }) => {
  if (status === 'verified') {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
        <span className="material-symbols-outlined !text-[14px]">check_circle</span>
        Verified
      </span>
    );
  }

  if (status === 'ai_approved') {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
        <span className="material-symbols-outlined !text-[14px]">smart_toy</span>
        AI Approved
      </span>
    );
  }

  return (
    <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-amber-50 text-amber-700 border border-amber-200">
      <span className="material-symbols-outlined !text-[14px]">schedule</span>
      Pending
    </span>
  );
};

interface CategoryBadgeProps {
  mcc: number;
  category?: string;
  showMccCode?: boolean;
}

export const CategoryBadge: React.FC<CategoryBadgeProps> = ({ mcc, category, showMccCode = true }) => {
  const style = getCategoryStyle(category ?? '');

  return (
    <div className="inline-flex items-center gap-1.5">
      <span
        className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium border ${style.badgeBg}`}
        title={`MCC ${mcc}: ${style.description}`}
      >
        <span className="material-symbols-outlined !text-[13px]">{style.icon}</span>
        <span>{category}</span>
      </span>
      {showMccCode && (
        <span
          className="text-[11px] font-mono text-slate-400 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200"
          title={`Merchant Category Code: ${mcc}`}
        >
          {mcc}
        </span>
     )}
    </div>
  );
};
