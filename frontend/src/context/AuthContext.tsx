'use client';

import { deleteCookie, getCookie } from '@/lib/api';
import { AuthContextType } from '@/lib/types';
import { usePathname, useRouter } from 'next/navigation';
import {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [jwtToken, setJwtToken] = useState<string>(() => getCookie('jwtToken'));
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    setJwtToken(getCookie('jwtToken'));
  }, [pathname]);

  useEffect(() => {
    const readToken = () => setJwtToken(getCookie('jwtToken'));

    window.addEventListener('focus', readToken);
    document.addEventListener('visibilitychange', readToken);

    return () => {
      window.removeEventListener('focus', readToken);
      document.removeEventListener('visibilitychange', readToken);
    };
  }, []);

  const logout = useCallback(() => {
    deleteCookie('jwtToken');
    router.replace('/login');
  }, [router]);

  const value = useMemo(
    () => ({ token: jwtToken, logout }),
    [jwtToken, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }

  return context;
};
