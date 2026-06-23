import type { UserRole } from '@/shared/types';

const WORKSPACE_PATHS = ['/vault', '/chat'];
const ADMIN_ONLY_PATHS = ['/teams'];
const SUPER_ADMIN_OR_ADMIN_PATHS = ['/users'];
const PUBLIC_AUTH_PATHS = ['/login', '/onboarding'];

function matchesPath(pathname: string, paths: string[]) {
  return paths.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

export function defaultPathForRole(role: UserRole | undefined): string {
  return role === 'SUPER_ADMIN' ? '/teams' : '/vault';
}

export function isPublicAuthPath(pathname: string): boolean {
  return matchesPath(pathname, PUBLIC_AUTH_PATHS);
}

export function isPathAllowedForRole(pathname: string, role: UserRole): boolean {
  if (matchesPath(pathname, WORKSPACE_PATHS)) return role !== 'SUPER_ADMIN';
  if (matchesPath(pathname, ADMIN_ONLY_PATHS)) return role === 'SUPER_ADMIN';
  if (matchesPath(pathname, SUPER_ADMIN_OR_ADMIN_PATHS)) {
    return role === 'SUPER_ADMIN' || role === 'ADMIN';
  }

  return pathname === defaultPathForRole(role);
}

export function safeRedirectPathForRole(from: string | null, role: UserRole | undefined): string {
  const fallback = defaultPathForRole(role);
  if (!from || !role || from.startsWith('//')) return fallback;

  try {
    const url = new URL(from, window.location.origin);
    if (url.origin !== window.location.origin) return fallback;
    if (isPublicAuthPath(url.pathname) || url.pathname.startsWith('/api')) return fallback;
    if (!isPathAllowedForRole(url.pathname, role)) return fallback;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return fallback;
  }
}
