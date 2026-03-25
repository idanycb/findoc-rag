'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogOut, LayoutDashboard, MessageSquare, History } from 'lucide-react';
import Button from '@/components/ui/Button';
import { useAuth } from '@/context/AuthContext';
import { ShieldCheck } from 'lucide-react';

const NAV_ITEMS = [
  { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { href: '/chat', label: 'AI Analyst', icon: MessageSquare },
  { href: '/audit', label: 'Audit', icon: History },
];

export const MainNavigation: React.FC = () => {
  const pathname = usePathname();
  const { logout } = useAuth();

  return (
    <aside className="w-80 bg-white border-r border-slate-100 flex flex-col z-30 shadow-sm relative">
      <div className="p-10">
        <div className="flex items-center gap-4 mb-14">
          <div className="bg-indigo-600 p-3 rounded-2xl text-white shadow-2xl shadow-indigo-200">
            <ShieldCheck size={28} />
          </div>
          <div>
            <h1 className="text-2xl font-black tracking-tight text-slate-800">
              FinDoc
            </h1>
            <p className="text-[10px] font-black text-indigo-400 tracking-[0.2em] uppercase">
              Document Analyzer
            </p>
          </div>
        </div>

        <nav className="space-y-2">
          {NAV_ITEMS.map(({ href, label, icon: Icon }) => {
            const isActive =
              pathname === href || pathname.startsWith(href + '/');

            return (
              <Link
                key={href}
                href={href}
                className={`w-full flex items-center gap-4 p-4 rounded-2xl transition-all duration-300 group ${
                  isActive
                    ? 'bg-indigo-600 text-white shadow-xl shadow-indigo-100 font-bold'
                    : 'text-slate-400 hover:bg-slate-50 hover:text-slate-700'
                }`}
              >
                <div
                  className={`${isActive ? 'text-white' : 'text-slate-300 group-hover:text-indigo-500'} transition-colors`}
                >
                  <Icon size={20} />
                </div>
                <span className="text-sm font-semibold tracking-tight">
                  {label}
                </span>
              </Link>
            );
          })}
        </nav>
      </div>

      <div className="mt-auto p-10 border-t border-slate-50">
        <div className="bg-slate-50 p-4 rounded-3xl mb-4 border border-slate-100 flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-indigo-100 flex items-center justify-center text-indigo-700 font-black">
            A
          </div>
          <div>
            <p className="text-xs font-black text-slate-800">Root Node</p>
            <p className="text-[10px] text-slate-400 font-bold uppercase tracking-widest">
              admin@finintel.io
            </p>
          </div>
        </div>
        <Button
          variant="ghost"
          className="w-full justify-start text-slate-400 hover:text-rose-600 hover:bg-rose-50"
          onClick={logout}
        >
          <LogOut size={18} /> Sign Out
        </Button>
      </div>
    </aside>
  );
};
