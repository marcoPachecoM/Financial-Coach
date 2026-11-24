import React from "react";

interface HeaderProps {
  onOpenUpload: () => void;
  /** Opens the CSV export dialog; hidden when there is no account yet. */
  onOpenExport?: () => void;
  onLogout: () => void;
  /** Opens the Security dialog (password and recovery codes). */
  onOpenSecurity: () => void;
  /** Opens/closes the sidebar (drawer on mobile, icon bar on desktop). */
  onToggleSidebar: () => void;
  /** Opens the "Add transaction" form; hidden when there is no account yet. */
  onOpenNewTransaction?: () => void;
  activeAccountName?: string | null;
}

export const Header: React.FC<HeaderProps> = ({
  onOpenUpload,
  onOpenExport,
  onLogout,
  onOpenSecurity,
  onToggleSidebar,
  onOpenNewTransaction,
  activeAccountName = null,
}) => {
  return (
    <header className="w-full bg-white border-b border-[#E2E8F0] px-4 md:px-8 py-3.5 flex items-center justify-between sticky top-0 z-40 shadow-xs">
      <div className="flex items-center gap-4">
        <button
          onClick={onToggleSidebar}
          title="Menu"
          className="p-2 -ml-2 rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800 cursor-pointer"
        >
          <span className="material-symbols-outlined !text-[22px]">menu</span>
        </button>
        {/* Brand Logo */}
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center text-white shadow-sm shadow-emerald-200 ring-2 ring-emerald-100">
            <span className="material-symbols-outlined !text-[22px]">eco</span>
          </div>
          <div className="flex flex-col">
            <h1 className="text-xl font-bold text-slate-900 tracking-tight leading-none flex items-center gap-1.5 font-sans">
              Mush
              <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800 tracking-normal uppercase">
                AI Coach
              </span>
            </h1>
            <span className="text-[10px] text-slate-500 uppercase tracking-wider font-semibold mt-1 font-mono">
              Autonomous Finance
            </span>
          </div>
        </div>

        {activeAccountName && (
          <>
            <div className="h-7 w-px bg-slate-200 mx-2 hidden md:block" />
            <span className="hidden md:flex items-center gap-2 text-sm text-slate-600">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-100" />
              <strong className="font-semibold text-slate-900">{activeAccountName}</strong>
            </span>
          </>
        )}
      </div>

      {/* Action CTA Buttons */}
      <div className="flex items-center gap-3">
        {onOpenNewTransaction && activeAccountName && (
          <button
            onClick={onOpenNewTransaction}
            className="bg-white hover:bg-emerald-50 text-emerald-700 border border-emerald-200 hover:border-emerald-300 px-4 py-2 rounded-xl text-sm font-semibold transition-all flex items-center gap-2 shadow-2xs cursor-pointer"
          >
            <span className="material-symbols-outlined !text-[18px]">add</span>
            <span className="hidden sm:inline">Add transaction</span>
            <span className="sm:hidden">Add</span>
          </button>
        )}
        {onOpenExport && (
          <button
            onClick={onOpenExport}
            title="Export transactions as CSV"
            aria-label="Export CSV"
            className="bg-white hover:bg-emerald-50 text-emerald-700 border border-emerald-200 hover:border-emerald-300 px-2.5 sm:px-4 py-2 rounded-xl text-sm font-semibold transition-all flex items-center gap-2 shadow-2xs cursor-pointer"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[18px]">download</span>
            {/* Icon only on phones: the header already has three buttons there. */}
            <span className="hidden sm:inline">Export CSV</span>
          </button>
        )}
        <button
          onClick={onOpenUpload}
          className="bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white px-4 py-2 rounded-xl text-sm font-semibold transition-all flex items-center gap-2 shadow-sm shadow-emerald-200 hover:shadow-md hover:shadow-emerald-200 cursor-pointer"
        >
          <span className="material-symbols-outlined !text-[18px]">
            upload_file
          </span>
          <span className="hidden sm:inline">Import CSV</span>
          <span className="sm:hidden">Import</span>
        </button>
        <button
          onClick={onOpenSecurity}
          title="Security"
          aria-label="Security"
          className="p-2 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 border border-transparent hover:border-slate-200 transition-all cursor-pointer"
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[20px]">shield_person</span>
        </button>
        <button
          onClick={onLogout}
          className="bg-slate-100 hover:bg-slate-200 active:bg-slate-300 text-slate-700 hover:text-slate-900 border border-slate-200 hover:border-slate-300 px-3.5 py-2 rounded-xl text-sm font-semibold transition-all flex items-center gap-1.5 shadow-2xs cursor-pointer"
        >
          <span className="material-symbols-outlined !text-[18px] text-slate-500">logout</span>
          <span className="hidden sm:inline">Log out</span>
          <span className="sm:hidden">Log out</span>
        </button>
      </div>
    </header>
  );
};
