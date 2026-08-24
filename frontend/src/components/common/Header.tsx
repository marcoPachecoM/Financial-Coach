import React, { useState } from "react";
import type { Connection } from "../../types/connection";

interface HeaderProps {
  onOpenUpload: () => void;
  onLogout: () => void;
  connections?: Connection[];
  activeConnectionId?: number | null;
  onSelectConnection?: (connectionId: number) => void;
  loadingConnections?: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  onOpenUpload,
  onLogout,
  connections = [],
  activeConnectionId = null,
  onSelectConnection,
  loadingConnections = false,
}) => {
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);

  const activeConnection =
    connections.find((c) => c.id === activeConnectionId) ||
    connections[0] ||
    null;

  const currentProfileName = activeConnection
    ? activeConnection.name
    : loadingConnections
    ? "Loading..."
    : "Primary Connection";

  return (
    <header className="w-full bg-white border-b border-[#E2E8F0] px-4 md:px-8 py-3.5 flex items-center justify-between sticky top-0 z-40 shadow-xs">
      <div className="flex items-center gap-4">
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

        {/* Divider */}
        <div className="h-7 w-px bg-slate-200 mx-2 hidden md:block" />

        {/* Database Connections / Profile Selector */}
        <div className="relative hidden md:block">
          <button
            onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-slate-100 transition-colors border border-transparent hover:border-slate-200 cursor-pointer"
          >
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-100 animate-pulse" />
            <span className="text-sm font-medium text-slate-800">
              Active Connection:{" "}
              <strong className="font-semibold text-slate-900">
                {currentProfileName}
              </strong>
            </span>
            <span className="material-symbols-outlined !text-[18px] text-slate-500 transition-transform duration-200">
              {profileDropdownOpen ? "expand_less" : "expand_more"}
            </span>
          </button>

          {/* Profile Dropdown */}
          {profileDropdownOpen && (
            <div
              className="absolute left-0 top-full mt-1.5 w-80 bg-white border border-slate-200 rounded-xl shadow-xl z-50 p-2 animate-in fade-in slide-in-from-top-1"
              onMouseLeave={() => setProfileDropdownOpen(false)}
            >
              <div className="px-2 py-1.5 border-b border-slate-100 mb-1 flex items-center justify-between">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
                  Database Connections
                </p>
                <span className="text-[10px] font-mono bg-slate-100 text-slate-500 px-1.5 py-0.5 rounded">
                  SQL Table
                </span>
              </div>
              <div className="space-y-1 max-h-64 overflow-y-auto">
                {connections.length > 0 ? (
                  connections.map((c) => {
                    const isSelected = activeConnection?.id === c.id;
                    const fileName = c.external_id
                      ? c.external_id.split(/[\\/]/).pop()
                      : `Dataset #${c.id}`;

                    return (
                      <button
                        key={c.id}
                        onClick={() => {
                          onSelectConnection?.(c.id);
                          setProfileDropdownOpen(false);
                        }}
                        className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-all flex items-start justify-between cursor-pointer ${
                          isSelected
                            ? "bg-emerald-50 text-emerald-900 font-medium"
                            : "hover:bg-slate-50 text-slate-700"
                        }`}
                      >
                        <div className="truncate mr-2">
                          <div className="flex items-center gap-1.5">
                            <span className="material-symbols-outlined !text-[16px] text-emerald-600">
                              storage
                            </span>
                            <p className="font-semibold text-slate-900 truncate">
                              {c.name}
                            </p>
                          </div>
                          <p
                            className="text-xs text-slate-400 font-mono truncate mt-0.5"
                            title={c.external_id}
                          >
                            {fileName}
                          </p>
                        </div>
                        {isSelected && (
                          <span className="material-symbols-outlined !text-[16px] text-emerald-600 mt-0.5 shrink-0">
                            check
                          </span>
                        )}
                      </button>
                    );
                  })
                ) : (
                  <div className="p-3 text-center text-xs text-slate-400">
                    {loadingConnections
                      ? "Loading database connections..."
                      : "No connections found in database."}
                  </div>
                )}
              </div>
              <div className="mt-2 pt-2 border-t border-slate-100 px-2 flex items-center justify-between text-[11px] text-slate-400">
                <span>SQL Server (SQLite)</span>
                <span className="font-mono">
                  {connections.length}{" "}
                  {connections.length === 1 ? "record" : "records"}
                </span>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Action CTA Buttons */}
      <div className="flex items-center gap-3">
        <button
          onClick={onOpenUpload}
          className="bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white px-4 py-2 rounded-xl text-sm font-semibold transition-all flex items-center gap-2 shadow-sm shadow-emerald-200 hover:shadow-md hover:shadow-emerald-200 cursor-pointer"
        >
          <span className="material-symbols-outlined !text-[18px]">
            add_circle
          </span>
          <span className="hidden sm:inline">Upload CSV Dataset</span>
          <span className="sm:hidden">Upload</span>
        </button>
        <button
          onClick={onLogout}
          className="bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 text-white px-4 py-2 rounded-xl text-sm font-semibold transition-all flex items-center gap-2 shadow-sm shadow-emerald-200 hover:shadow-md hover:shadow-emerald-200 cursor-pointer"
        >
          <span className="material-symbols-outlined !text-[18px]">logout</span>
          <span className="hidden sm:inline">Log out</span>
          <span className="sm:hidden">log out</span>
        </button>
      </div>
    </header>
  );
};
