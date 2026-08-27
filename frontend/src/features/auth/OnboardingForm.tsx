'use client';

import { useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { AlertTriangle, User, Lock, Check, Plus } from 'lucide-react';
import { apiCall, ApiError } from '@/shared/lib/api';

export function OnboardingForm() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isPending, startTransition] = useTransition();
  const router = useRouter();

  const passwordOk = password.length >= 8;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    startTransition(async () => {
      try {
        await apiCall('/onboarding', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
        });
        router.replace('/login');
      } catch (err) {
        if (err instanceof ApiError && err.status === 409) {
          setError('System already initialized. Redirecting to login…');
          setTimeout(() => router.replace('/login'), 1500);
        } else {
          setError(
            err instanceof ApiError ? err.message : 'Initialization failed. Please try again.'
          );
        }
      }
    });
  };

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-105 mx-auto">
      <div className="mb-6 flex items-center gap-2.5 rounded-[10px] border border-[#F59E0B] bg-[#FFFBEB] px-4 py-3">
        <AlertTriangle size={16} className="flex-none text-[#D97706]" />
        <p className="text-[13px] leading-[1.45] text-[#92400E]">
          <span className="font-bold">First boot detected.</span> No users exist yet. Create the
          initial super admin to get started.
        </p>
      </div>

      <div className="rounded-2xl bg-white p-7 shadow-[0_2px_16px_rgba(0,0,0,.07)] sm:p-9">
        <h1 className="text-[22px] font-bold tracking-[-.01em] text-[#111111]">
          Initialize system
        </h1>
        <p className="mt-1.5 text-sm text-[#888888]">
          Create the initial <strong className="font-semibold text-[#111111]">SUPER_ADMIN</strong>{' '}
          account.
        </p>

        <div className="mt-7 flex flex-col gap-4.5">
          <div>
            <label className="text-[13px] font-medium text-[#444444]">Username</label>
            <div className="mt-1.75 flex h-12 items-center gap-2.5 rounded-[9px] border border-[#E8E8E8] bg-[#F7F7F7] px-3.5 transition-colors focus-within:border-[#111111]">
              <User size={16} className="text-[#B0B0B0] flex-none" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="super_admin"
                required
                autoComplete="username"
                className="flex-1 bg-transparent text-[15px] text-[#111111] placeholder:text-[#B0B0B0] outline-hidden"
              />
            </div>
          </div>

          <div>
            <label className="text-[13px] font-medium text-[#444444]">Password</label>
            <div className="mt-1.75 flex h-12 items-center gap-2.5 rounded-[9px] border border-[#E8E8E8] bg-[#F7F7F7] px-3.5 transition-colors focus-within:border-[#111111]">
              <Lock size={16} className="text-[#B0B0B0] flex-none" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Min. 8 characters"
                required
                minLength={8}
                autoComplete="new-password"
                className="flex-1 bg-transparent text-[15px] text-[#111111] placeholder:text-[#B0B0B0] outline-hidden"
              />
            </div>
            <p
              className={`mt-1.75 flex items-center gap-1.5 text-xs transition-colors ${passwordOk ? 'text-[#6B6B6B]' : 'text-[#999999]'}`}
            >
              <Check
                size={13}
                strokeWidth={2.5}
                className={passwordOk ? 'text-[#22C55E]' : 'text-[#BBBBBB]'}
              />
              Minimum 8 characters required
            </p>
          </div>
        </div>

        {error && (
          <div className="mt-4 rounded-[9px] border border-[#FECACA] bg-[#FEF2F2] px-4 py-3 text-[13px] text-[#B91C1C]">
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={isPending || !passwordOk}
          className="mt-6.5 flex h-12.5 w-full items-center justify-center gap-2 rounded-[11px] bg-[#111111] text-[15px] font-semibold text-white transition-colors hover:bg-[#333333] disabled:opacity-60 sm:h-11.5 sm:rounded-[9px]"
        >
          <Plus size={16} />
          {isPending ? 'Creating…' : 'Create super admin'}
        </button>
      </div>
    </form>
  );
}
