import React from 'react';
import type { TransactionStatus } from '../../types/transaction';
import { getCategoryFromMCC } from '../../utils/mccHelper';

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
  mcc: string;
  showMccCode?: boolean;
}

export const CategoryBadge: React.FC<CategoryBadgeProps> = ({ mcc, showMccCode = true }) => {
  const info = getCategoryFromMCC(mcc);

  return (
    <div className="inline-flex items-center gap-1.5">
      <span
        className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium border ${info.badgeBg}`}
        title={`MCC ${mcc}: ${info.description}`}
      >
        <span className="material-symbols-outlined !text-[13px]">{info.icon}</span>
        <span>{info.category}</span>
      </span>
      {showMccCode && mcc && mcc !== '0000' && (
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
