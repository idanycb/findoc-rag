'use client';

import { OnboardingForm } from '@/features/auth/OnboardingForm';
import { useAuth } from '@/context/AuthContext';
import { apiCall } from '@/shared/lib/api';
import { FileText } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

export default function OnboardingPage() {
  const { claims, isHydrated } = useAuth();
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!isHydrated) return;

    if (claims) {
      router.replace(claims.role === 'SUPER_ADMIN' ? '/teams' : '/vault');
      return;
    }

    apiCall<{ enabled: boolean }>('/onboarding/status').then(
      (res) => {
        if (res?.enabled) {
          setReady(true);
        } else {
          router.replace('/login');
        }
      },
      () => router.replace('/login')
    );
  }, [isHydrated, claims, router]);

  if (!ready) {
    return (
      <div className="min-h-screen bg-[#EBEBEB] flex items-center justify-center">
        <div className="h-5 w-5 animate-spin rounded-full border-2 border-[#111111] border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#EBEBEB] flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-120">
        <div className="mb-8 flex items-center justify-center gap-2.75">
          <div className="flex h-9.5 w-9.5 items-center justify-center rounded-[11px] bg-[#111111]">
            <FileText size={20} className="text-white" strokeWidth={2.2} />
          </div>
          <span className="text-base font-bold text-[#111111]">FinDoc Analyzer</span>
        </div>
        <OnboardingForm />
      </div>
    </div>
  );
}
