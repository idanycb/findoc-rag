import { type NextRequest, NextResponse } from 'next/server';

function decodeJwtRole(token: string): string | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const padded = payload.replace(/-/g, '+').replace(/_/g, '/');
    const decoded = JSON.parse(atob(padded + '=='.slice((padded.length + 2) % 4 || 4)));
    return decoded?.role ?? null;
  } catch {
    return null;
  }
}

const PUBLIC_PATHS = ['/login', '/onboarding'];
const WORKSPACE_PATHS = ['/vault', '/chat'];
const ADMIN_ONLY_PATHS = ['/teams'];
const SUPER_ADMIN_OR_ADMIN_PATHS = ['/users'];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get('jwtToken')?.value;

  const isPublic = PUBLIC_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/'));
  const isWorkspace = WORKSPACE_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/'));
  const isAdminOnly = ADMIN_ONLY_PATHS.some((p) => pathname === p || pathname.startsWith(p + '/'));
  const isSuperOrAdmin = SUPER_ADMIN_OR_ADMIN_PATHS.some(
    (p) => pathname === p || pathname.startsWith(p + '/')
  );

  if (pathname === '/') {
    if (!token) return NextResponse.redirect(new URL('/login', request.url));
    const role = decodeJwtRole(token);
    const dest = role === 'SUPER_ADMIN' ? '/teams' : '/vault';
    return NextResponse.redirect(new URL(dest, request.url));
  }

  if (isPublic && token) {
    const role = decodeJwtRole(token);
    const dest = role === 'SUPER_ADMIN' ? '/teams' : '/vault';
    return NextResponse.redirect(new URL(dest, request.url));
  }

  if ((isWorkspace || isAdminOnly || isSuperOrAdmin) && !token) {
    const url = new URL('/login', request.url);
    url.searchParams.set('from', pathname);
    return NextResponse.redirect(url);
  }

  if (isWorkspace && token) {
    const role = decodeJwtRole(token);
    if (role === 'SUPER_ADMIN') {
      return NextResponse.redirect(new URL('/teams', request.url));
    }
  }

  if (isAdminOnly && token) {
    const role = decodeJwtRole(token);
    if (role !== 'SUPER_ADMIN') {
      return NextResponse.redirect(new URL('/vault', request.url));
    }
  }

  if (isSuperOrAdmin && token) {
    const role = decodeJwtRole(token);
    if (role !== 'SUPER_ADMIN' && role !== 'ADMIN') {
      return NextResponse.redirect(new URL('/vault', request.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/((?!_next/static|_next/image|favicon.ico|api/).*)'],
};
