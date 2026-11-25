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
    <header className="w-full bg-white border-b border-[#E2E8F0] px-3 sm:px-6 lg:px-8 py-2.5 sm:py-3.5 flex items-center justify-between gap-2 sticky top-0 z-40 shadow-xs">
      <div className="flex items-center gap-2 sm:gap-4 min-w-0">
        <button
          onClick={onToggleSidebar}
          title="Menu"
          aria-label="Menu"
          className="p-2 -ml-1.5 sm:-ml-2 rounded-lg text-slate-500 hover:bg-slate-100 hover:text-slate-800 shrink-0 cursor-pointer"
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[22px]">menu</span>
        </button>
        {/* Brand Logo */}
        <div className="flex items-center gap-2 sm:gap-3 min-w-0">
          <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center text-white shadow-sm shadow-emerald-200 ring-2 ring-emerald-100 shrink-0">
            <span className="material-symbols-outlined !text-[20px] sm:!text-[22px]">eco</span>
          </div>
          <div className="flex flex-col min-w-0">
            <h1 className="text-lg sm:text-xl font-bold text-slate-900 tracking-tight leading-none flex items-center gap-1.5 font-sans whitespace-nowrap">
              Mush
              <span className="hidden sm:inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-emerald-100 text-emerald-800 tracking-normal uppercase whitespace-nowrap">
                AI Coach
              </span>
            </h1>
            <span className="hidden sm:block text-[10px] text-slate-500 uppercase tracking-wider font-semibold mt-1 font-mono whitespace-nowrap">
              Autonomous Finance
            </span>
          </div>
        </div>

        {activeAccountName && (
          <>
            <div className="h-7 w-px bg-slate-200 mx-2 hidden lg:block shrink-0" />
            <span className="hidden lg:flex items-center gap-2 text-sm text-slate-600 min-w-0">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-100 shrink-0" />
              <strong className="font-semibold text-slate-900 truncate max-w-[180px]">{activeAccountName}</strong>
            </span>
          </>
        )}
      </div>

      {/* Action CTA Buttons */}
      <div className="flex items-center gap-1 sm:gap-2.5 xl:gap-3 shrink-0">
        {onOpenNewTransaction && activeAccountName && (
          <button
            onClick={onOpenNewTransaction}
            title="Add transaction"
            aria-label="Add transaction"
            className="bg-white hover:bg-emerald-50 text-emerald-700 border border-emerald-200 hover:border-emerald-300 h-9 w-9 sm:h-auto sm:w-auto sm:px-3.5 sm:py-2 xl:px-4 rounded-xl text-sm font-semibold transition-all flex items-center justify-center gap-2 shadow-2xs whitespace-nowrap cursor-pointer"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[18px]">add</span>
            <span className="hidden xl:inline">Add transaction</span>
            <span className="hidden sm:inline xl:hidden">Add</span>
          </button>
        )}
        {onOpenExport && (
          <button
            onClick={onOpenExport}
            title="Export transactions as CSV"
            aria-label="Export CSV"
            className="bg-white hover:bg-emerald-50 text-emerald-700 border border-emerald-200 hover:border-emerald-300 h-9 w-9 sm:h-auto sm:w-auto sm:px-2.5 xl:px-4 sm:py-2 rounded-xl text-sm font-semibold transition-all flex items-center justify-center gap-2 shadow-2xs whitespace-nowrap cursor-pointer"
          >
            <span aria-hidden="true" className="material-symbols-outlined !text-[18px]">download</span>
            {/* Icon only on smaller screens so all actions fit cleanly. */}
            <span className="hidden xl:inline">Export CSV</span>
          </button>
        )}
        <button
          onClick={onOpenUpload}
          title="Import CSV"
          aria-label="Import CSV"
          className={`bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white border border-emerald-600 hover:border-emerald-700 ${
            activeAccountName ? "h-9 w-9" : "h-9 px-3"
          } sm:h-auto sm:w-auto sm:px-3.5 sm:py-2 xl:px-4 rounded-xl text-sm font-semibold transition-all flex items-center justify-center gap-1.5 sm:gap-2 shadow-sm shadow-emerald-200 hover:shadow-md hover:shadow-emerald-200 whitespace-nowrap cursor-pointer`}
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[18px]">
            upload_file
          </span>
          <span className="hidden xl:inline">Import CSV</span>
          <span className={activeAccountName ? "hidden sm:inline xl:hidden" : "text-xs sm:text-sm xl:hidden"}>Import</span>
        </button>
        <button
          onClick={onOpenSecurity}
          title="Security"
          aria-label="Security"
          className="h-9 w-9 sm:h-auto sm:w-auto sm:p-2 rounded-xl text-slate-500 hover:text-slate-800 hover:bg-slate-100 border border-transparent hover:border-slate-200 transition-all flex items-center justify-center cursor-pointer"
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[19px] sm:!text-[20px]">shield_person</span>
        </button>
        <button
          onClick={onLogout}
          title="Log out"
          aria-label="Log out"
          className="bg-slate-100 hover:bg-slate-200 active:bg-slate-300 text-slate-700 hover:text-slate-900 border border-slate-200 hover:border-slate-300 h-9 w-9 md:h-auto md:w-auto md:px-3.5 md:py-2 rounded-xl text-sm font-semibold transition-all flex items-center justify-center gap-1.5 shadow-2xs whitespace-nowrap cursor-pointer"
        >
          <span aria-hidden="true" className="material-symbols-outlined !text-[18px] text-slate-500">logout</span>
          <span className="hidden md:inline">Log out</span>
        </button>
      </div>
    </header>
  );
};
