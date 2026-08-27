'use client';

import { useEffect, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { User, Lock, LogIn } from 'lucide-react';
import { apiCall, setCookie, ApiError } from '@/shared/lib/api';
import { decodeJwt } from '@/shared/lib/auth';
import {
  defaultPathForRole,
  safeRedirectPathForRole,
} from '@/shared/lib/routes';
import { useAuth } from '@/context/AuthContext';

export function LoginForm() {
  const { claims, isHydrated } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [onboardingChecked, setOnboardingChecked] = useState(false);
  const [isPending, startTransition] = useTransition();
  const router = useRouter();

  useEffect(() => {
    if (!isHydrated) return;

    if (claims) {
      router.replace(defaultPathForRole(claims.role));
      return;
    }

    let cancelled = false;
    apiCall<{ enabled: boolean }>('/onboarding/status').then(
      (status) => {
        if (cancelled) return;
        if (status?.enabled) {
          router.replace('/onboarding');
        } else {
          setOnboardingChecked(true);
        }
      },
      () => {
        if (!cancelled) setOnboardingChecked(true);
      }
    );

    return () => {
      cancelled = true;
    };
  }, [claims, isHydrated, router]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    startTransition(async () => {
      try {
        const data = await apiCall<{ accessToken: string }>('/auth/login', {
          method: 'POST',
          body: JSON.stringify({ username, password }),
        });
        if (!data?.accessToken) throw new Error('No token received');
        setCookie('jwtToken', data.accessToken);
        const claims = decodeJwt(data.accessToken);
        const from =
          typeof window === 'undefined'
            ? null
            : new URLSearchParams(window.location.search).get('from');
        router.replace(safeRedirectPathForRole(from, claims?.role));
      } catch (err) {
        setError(
          err instanceof ApiError
            ? err.message
            : 'Login failed. Please try again.'
        );
      }
    });
  };

  if (!isHydrated || claims || !onboardingChecked) {
    return (
      <div className="flex min-h-65 items-center justify-center">
        <div className="h-5 w-5 animate-spin rounded-full border-2 border-[#111111] border-t-transparent" />
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-95 mx-auto">
      <h1 className="hidden text-2xl font-bold tracking-[-.01em] text-[#111111] md:block">
        Sign in
      </h1>
      <p className="mt-1.5 hidden text-sm text-[#888888] md:block">
        Enter your credentials to access your vault.
      </p>

      <div className="mt-0 flex flex-col gap-4.5 md:mt-8">
        <div>
          <label className="text-[13px] font-medium text-[#444444]">
            Username
          </label>
          <div className="mt-1.75 flex h-12 items-center gap-2.5 rounded-[9px] border border-[#E8E8E8] bg-white px-3.5 transition-colors focus-within:border-[#111111] md:h-11.5 md:bg-[#F7F7F7]">
            <User size={16} className="text-[#B0B0B0] flex-none" />
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="jane.admin"
              required
              autoComplete="username"
              className="flex-1 bg-transparent text-[15px] text-[#111111] placeholder:text-[#B0B0B0] outline-hidden"
            />
          </div>
        </div>

        <div>
          <label className="text-[13px] font-medium text-[#444444]">
            Password
          </label>
          <div className="mt-1.75 flex h-12 items-center gap-2.5 rounded-[9px] border border-[#E8E8E8] bg-white px-3.5 transition-colors focus-within:border-[#111111] md:h-11.5 md:bg-[#F7F7F7]">
            <Lock size={16} className="text-[#B0B0B0] flex-none" />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              required
              autoComplete="current-password"
              className="flex-1 bg-transparent text-[15px] text-[#111111] placeholder:text-[#B0B0B0] outline-hidden"
            />
          </div>
        </div>
      </div>

      {error && (
        <div className="mt-4 rounded-[9px] border border-[#FECACA] bg-[#FEF2F2] px-4 py-3 text-[13px] text-[#B91C1C]">
          {error}
        </div>
      )}

      <button
        type="submit"
        disabled={isPending}
        className="mt-5.5 flex h-12.5 w-full items-center justify-center gap-2 rounded-[11px] bg-[#111111] text-[15px] font-semibold text-white transition-colors hover:bg-[#333333] disabled:opacity-60 md:mt-6 md:h-11.5 md:rounded-[9px]"
      >
        {isPending ? 'Signing in…' : 'Sign in'}
        {!isPending && <LogIn size={16} />}
      </button>
    </form>
  );
}
