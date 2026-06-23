'use client';

import { deleteCookie, getCookie } from '@/shared/lib/api';
import { decodeJwt, isTokenExpired } from '@/shared/lib/auth';
import { isPublicAuthPath } from '@/shared/lib/routes';
import type { AuthContextType, JwtClaims } from '@/shared/types';
import { usePathname, useRouter } from 'next/navigation';
import {
  createContext,
  ReactNode,
  startTransition,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [jwtToken, setJwtToken] = useState('');
  const [claims, setClaims] = useState<JwtClaims | null>(null);
  const [isHydrated, setIsHydrated] = useState(false);
  const router = useRouter();
  const pathname = usePathname();

  const readToken = useCallback(() => {
    startTransition(() => {
      const t = getCookie('jwtToken');
      const nextClaims = t ? decodeJwt(t) : null;

      if (t && (!nextClaims || isTokenExpired(nextClaims))) {
        deleteCookie('jwtToken');
        setJwtToken('');
        setClaims(null);
        setIsHydrated(true);
        if (!isPublicAuthPath(pathname)) {
          router.replace('/login');
        }
        return;
      }

      setJwtToken(t);
      setClaims(nextClaims);
      setIsHydrated(true);
    });
  }, [pathname, router]);

  useEffect(() => {
    readToken();
  }, [pathname, readToken]);

  useEffect(() => {
    window.addEventListener('focus', readToken);
    document.addEventListener('visibilitychange', readToken);
    return () => {
      window.removeEventListener('focus', readToken);
      document.removeEventListener('visibilitychange', readToken);
    };
  }, [readToken]);

  const logout = useCallback(() => {
    deleteCookie('jwtToken');
    router.replace('/login');
  }, [router]);

  const value = useMemo(
    () => ({ token: jwtToken, claims, isHydrated, logout }),
    [jwtToken, claims, isHydrated, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
