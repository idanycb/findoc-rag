'use client';

import { useAuth } from '@/context/AuthContext';
import { defaultPathForRole } from '@/shared/lib/routes';
import type { UserRole } from '@/shared/types';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, useMemo } from 'react';

export function useRequireRole(allowedRoles: UserRole[]) {
  const { token, claims, isHydrated } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const allowedRolesKey = allowedRoles.join('|');
  const allowedRoleSet = useMemo(
    () => new Set(allowedRolesKey.split('|') as UserRole[]),
    [allowedRolesKey]
  );
  const isAuthorized = Boolean(claims && allowedRoleSet.has(claims.role));

  useEffect(() => {
    if (!isHydrated) return;

    if (!token || !claims) {
      router.replace(`/login?from=${encodeURIComponent(pathname)}`);
      return;
    }

    if (!allowedRoleSet.has(claims.role)) {
      router.replace(defaultPathForRole(claims.role));
    }
  }, [allowedRoleSet, claims, isHydrated, pathname, router, token]);

  return {
    isAuthorized: isHydrated && isAuthorized,
    isCheckingAccess: !isHydrated || !isAuthorized,
  };
}
