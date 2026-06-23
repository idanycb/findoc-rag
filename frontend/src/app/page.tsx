'use client';

import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export default function RootPage() {
  const { token, claims } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!token) {
      router.replace('/login');
    } else if (claims?.role === 'SUPER_ADMIN') {
      router.replace('/teams');
    } else {
      router.replace('/vault');
    }
  }, [token, claims, router]);

  return null;
}
