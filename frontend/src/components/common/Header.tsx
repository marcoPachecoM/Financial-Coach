import React, { useState } from "react";

interface HeaderProps {
  onOpenUpload: () => void;
  onLogout: () => void;
  activeProfileName?: string;
}

export const Header: React.FC<HeaderProps> = ({
  onOpenUpload,
  onLogout,
  activeProfileName = "Alex",
}) => {
  const [profileDropdownOpen, setProfileDropdownOpen] = useState(false);
  const [currentProfile, setCurrentProfile] = useState(activeProfileName);

  const profiles = [
    {
      name: "Alex",
      type: "Personal Account",
      desc: "Primary checking & high-yield savings",
    },
    {
      name: "Alex & Partner",
      type: "Joint Household",
      desc: "Shared utilities and rent account",
    },
    {
      name: "Nexus Consulting LLC",
      type: "Business Entity",
      desc: "S-Corp corporate entity",
    },
  ];

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

        {/* Scalability / Profile Selector */}
        <div className="relative hidden md:block">
          <button
            onClick={() => setProfileDropdownOpen(!profileDropdownOpen)}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg hover:bg-slate-100 transition-colors border border-transparent hover:border-slate-200"
          >
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 ring-4 ring-emerald-100 animate-pulse" />
            <span className="text-sm font-medium text-slate-800">
              Active Profile:{" "}
              <strong className="font-semibold text-slate-900">
                {currentProfile}
              </strong>
            </span>
            <span className="material-symbols-outlined !text-[18px] text-slate-500 transition-transform duration-200">
              {profileDropdownOpen ? "expand_less" : "expand_more"}
            </span>
          </button>

          {/* Profile Dropdown */}
          {profileDropdownOpen && (
            <div
              className="absolute left-0 top-full mt-1.5 w-72 bg-white border border-slate-200 rounded-xl shadow-xl z-50 p-2 animate-in fade-in slide-in-from-top-1"
              onMouseLeave={() => setProfileDropdownOpen(false)}
            >
              <div className="px-2 py-1.5 border-b border-slate-100 mb-1">
                <p className="text-xs font-semibold uppercase tracking-wider text-slate-400 font-mono">
                  Switch Entity / Profile
                </p>
              </div>
              <div className="space-y-1">
                {profiles.map((p) => (
                  <button
                    key={p.name}
                    onClick={() => {
                      setCurrentProfile(p.name);
                      setProfileDropdownOpen(false);
                    }}
                    className={`w-full text-left px-3 py-2 rounded-lg text-sm transition-all flex items-start justify-between ${
                      currentProfile === p.name
                        ? "bg-emerald-50 text-emerald-900 font-medium"
                        : "hover:bg-slate-50 text-slate-700"
                    }`}
                  >
                    <div>
                      <p className="font-medium text-slate-900">{p.name}</p>
                      <p className="text-xs text-slate-500">{p.desc}</p>
                    </div>
                    {currentProfile === p.name && (
                      <span className="material-symbols-outlined !text-[16px] text-emerald-600 mt-0.5">
                        check
                      </span>
                    )}
                  </button>
                ))}
              </div>
              <div className="mt-2 pt-2 border-t border-slate-100 px-2">
                <span className="text-[11px] text-slate-400">
                  Multi-Entity & Plaid Ready
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
      {/* Action CTA Button */}
    </header>
  );
};
