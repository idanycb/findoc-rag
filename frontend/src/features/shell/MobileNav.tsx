'use client';

import { useAuth } from '@/context/AuthContext';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { FolderOpen, Landmark, MessageSquare, Users, UsersRound } from 'lucide-react';

interface MobileNavItemProps {
  href: string;
  icon: React.ReactNode;
  label: string;
  active: boolean;
}

function MobileNavItem({ href, icon, label, active }: MobileNavItemProps) {
  return (
    <Link
      href={href}
      className={`flex flex-col items-center gap-[5px] flex-1 py-2 text-[10.5px] font-semibold transition-colors ${
        active ? 'text-[#111111]' : 'text-[#BBBBBB]'
      }`}
    >
      {icon}
      {label}
    </Link>
  );
}

export function MobileNav() {
  const { claims, isHydrated } = useAuth();
  const pathname = usePathname();

  if (!isHydrated || !claims) return null;

  const isSuperAdmin = claims.role === 'SUPER_ADMIN';
  const isAdmin = claims.role === 'ADMIN';

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-white border-t border-[#F0F0F0] flex items-stretch z-50 md:hidden pt-[10px] pb-[14px]">
      {isSuperAdmin ? (
        <>
          <MobileNavItem
            href="/teams"
            icon={<UsersRound size={20} />}
            label="Teams"
            active={pathname === '/teams' || pathname.startsWith('/teams/')}
          />
          <MobileNavItem
            href="/users"
            icon={<Users size={20} />}
            label="Users"
            active={pathname === '/users'}
          />
        </>
      ) : (
        <>
          <MobileNavItem
            href="/vault"
            icon={<FolderOpen size={20} />}
            label="Workspace"
            active={pathname === '/vault' || pathname.startsWith('/vault/')}
          />
          <MobileNavItem
            href="/chat"
            icon={<MessageSquare size={20} />}
            label="Chat"
            active={pathname === '/chat'}
          />
          <MobileNavItem
            href="/edgar"
            icon={<Landmark size={20} />}
            label="EDGAR"
            active={pathname === '/edgar'}
          />
          {isAdmin && (
            <MobileNavItem
              href="/users"
              icon={<Users size={20} />}
              label="Users"
              active={pathname === '/users'}
            />
          )}
        </>
      )}
    </nav>
  );
}
