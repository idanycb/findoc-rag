'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LogOut,
  LayoutDashboard,
  MessageSquare,
  History,
  Menu,
  X,
  ShieldCheck,
} from 'lucide-react';
import Button from '@/components/ui/Button';
import { useAuth } from '@/context/AuthContext';

const NAV_ITEMS = [
  { href: '/dashboard', label: 'Workspace', icon: LayoutDashboard },
  { href: '/chat', label: 'RAG Chat', icon: MessageSquare },
  { href: '/audit', label: 'Activity Log', icon: History },
];

export const MainNavigation: React.FC = () => {
  const pathname = usePathname();
  const { logout } = useAuth();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const renderNavigation = (onNavigate?: () => void) => (
    <nav className="space-y-2">
      {NAV_ITEMS.map(({ href, label, icon: Icon }) => {
        const isActive = pathname === href || pathname.startsWith(href + '/');

        return (
          <Link
            key={href}
            href={href}
            onClick={onNavigate}
            className={`group flex w-full items-center gap-4 rounded-2xl p-4 transition-all duration-300 ${
              isActive
                ? 'bg-neutral-900 text-white shadow-xl shadow-neutral-300 font-bold'
                : 'text-neutral-500 hover:bg-neutral-100 hover:text-neutral-900'
            }`}
          >
            <div
              className={`${isActive ? 'text-white' : 'text-neutral-400 group-hover:text-neutral-900'} transition-colors`}
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
  );

  const handleLogout = () => {
    setIsMobileMenuOpen(false);
    logout();
  };

  const accountPanel = (
    <>
      <div className="mb-4 flex items-center gap-3 rounded-3xl border border-neutral-200 bg-neutral-100 p-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-full bg-neutral-300 font-black text-neutral-900">
          D
        </div>
        <div>
          <p className="text-xs font-black text-neutral-900">Demo User</p>
        </div>
      </div>
      <Button
        variant="ghost"
        className="w-full justify-start text-neutral-600 hover:bg-neutral-200 hover:text-black"
        onClick={handleLogout}
      >
        <LogOut size={18} /> Sign Out
      </Button>
    </>
  );

  return (
    <>
      <div className="sticky top-0 z-40 flex items-center justify-between border-b border-neutral-200 bg-white px-4 py-3 md:hidden">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-neutral-900 p-2 text-white">
            <ShieldCheck size={18} />
          </div>
          <div className="text-sm font-black tracking-tight text-neutral-900">
            RAG Workspace
          </div>
        </div>
        <button
          type="button"
          onClick={() => setIsMobileMenuOpen(true)}
          className="rounded-xl border border-neutral-300 bg-neutral-100 p-2 text-neutral-700"
          aria-label="Open navigation"
        >
          <Menu size={18} />
        </button>
      </div>

      <aside className="relative z-30 hidden w-72 shrink-0 flex-col border-r border-neutral-200 bg-white shadow-sm md:flex">
        <div className="p-8">
          <div className="mb-10 flex items-center gap-4">
            <div className="rounded-2xl bg-neutral-900 p-3 text-white shadow-2xl shadow-neutral-300">
              <ShieldCheck size={28} />
            </div>
            <div>
              <h1 className="text-2xl font-black tracking-tight text-neutral-900">
                RAG
              </h1>
              <p className="text-[10px] font-black uppercase tracking-[0.2em] text-neutral-500">
                Workspace
              </p>
            </div>
          </div>
          {renderNavigation()}
        </div>

        <div className="mt-auto border-t border-neutral-200 p-8">
          {accountPanel}
        </div>
      </aside>

      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-50 md:hidden">
          <button
            type="button"
            className="absolute inset-0 bg-black/45"
            onClick={() => setIsMobileMenuOpen(false)}
            aria-label="Close navigation overlay"
          />
          <aside className="relative h-full w-72 max-w-[85vw] border-r border-neutral-200 bg-white shadow-2xl">
            <div className="flex items-center justify-between border-b border-neutral-200 p-5">
              <div className="flex items-center gap-3">
                <div className="rounded-xl bg-neutral-900 p-2 text-white">
                  <ShieldCheck size={18} />
                </div>
                <p className="text-sm font-black tracking-tight text-neutral-900">
                  RAG Workspace
                </p>
              </div>
              <button
                type="button"
                onClick={() => setIsMobileMenuOpen(false)}
                className="rounded-xl border border-neutral-300 bg-neutral-100 p-2 text-neutral-700"
                aria-label="Close navigation"
              >
                <X size={18} />
              </button>
            </div>

            <div className="flex h-[calc(100%-4.5rem)] flex-col justify-between p-6">
              {renderNavigation(() => setIsMobileMenuOpen(false))}
              <div>{accountPanel}</div>
            </div>
          </aside>
        </div>
      )}
    </>
  );
};
