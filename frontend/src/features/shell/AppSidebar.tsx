'use client';

import { useAuth } from '@/context/AuthContext';
import { getUserInitials } from '@/shared/lib/auth';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  FileText,
  FolderOpen,
  MessageSquare,
  Users,
  UsersRound,
  LogOut,
} from 'lucide-react';

interface NavItemProps {
  href: string;
  icon: React.ReactNode;
  label: string;
  active: boolean;
}

function NavItem({ href, icon, label, active }: NavItemProps) {
  return (
    <Link
      href={href}
      className={`flex items-center gap-[11px] px-3 py-[10px] rounded-[10px] text-sm transition-colors ${
        active
          ? 'bg-[#111111] text-white font-semibold'
          : 'text-[#666666] hover:bg-[#F5F5F5] hover:text-[#111111]'
      }`}
    >
      {icon}
      {label}
    </Link>
  );
}

export function AppSidebar() {
  const { claims, isHydrated, logout } = useAuth();
  const pathname = usePathname();

  if (!isHydrated || !claims) return null;

  const isSuperAdmin = claims.role === 'SUPER_ADMIN';
  const isAdmin = claims.role === 'ADMIN';
  const initials = getUserInitials(claims.sub);

  const roleColor = isSuperAdmin ? '#D97706' : '#999999';
  const roleLabel = isSuperAdmin
    ? 'SUPER_ADMIN'
    : isAdmin
      ? `ADMIN${claims.teamId ? '' : ''}`
      : 'MEMBER';

  const avatarGradient = isSuperAdmin
    ? '#FDE68A'
    : '#E0E0E0';

  const avatarTextColor = isSuperAdmin ? '#92400E' : '#555555';

  return (
    <aside className="w-[220px] flex-none bg-white border-r border-[#F0F0F0] flex flex-col py-4 px-3 h-screen sticky top-0">
      {/* Logo */}
      <div className="flex items-center gap-[10px] px-[10px] py-2 mb-3">
        <div className="w-9 h-9 rounded-[10px] bg-[#111111] flex items-center justify-center flex-none">
          <FileText size={18} className="text-white" strokeWidth={2.2} />
        </div>
        <div>
          <div className="text-sm font-bold text-[#111111] leading-[1.1]">FinDoc</div>
          <div className="text-[10px] text-[#AAAAAA] uppercase tracking-[.08em]">
            {isSuperAdmin ? 'Admin' : 'Workspace'}
          </div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex flex-col gap-0.5 flex-1 min-h-0">
        {isSuperAdmin ? (
          <>
            <NavItem
              href="/teams"
              icon={<UsersRound size={17} />}
              label="Teams"
              active={pathname === '/teams' || pathname.startsWith('/teams/')}
            />
            <NavItem
              href="/users"
              icon={<Users size={17} />}
              label="Users"
              active={pathname === '/users' || pathname.startsWith('/users/')}
            />
            <div className="mt-[14px] bg-[#FFFBEB] border border-[#FDE68A] rounded-[10px] p-[10px] px-3">
              <p className="text-[11px] text-[#92400E] leading-[1.5]">
                <span className="font-bold">System administrator</span> - not assigned to a
                team. Manages all teams and users.
              </p>
            </div>
          </>
        ) : (
          <>
            <NavItem
              href="/vault"
              icon={<FolderOpen size={17} />}
              label="Workspace"
              active={pathname === '/vault' || pathname.startsWith('/vault/')}
            />
            <NavItem
              href="/chat"
              icon={<MessageSquare size={17} />}
              label="RAG Chat"
              active={pathname === '/chat'}
            />
            {isAdmin && (
              <NavItem
                href="/users"
                icon={<Users size={17} />}
                label="Users"
                active={pathname === '/users'}
              />
            )}
          </>
        )}
      </nav>

      {/* Footer */}
      <div>
        <div className="bg-[#F5F5F5] rounded-xl p-[10px] px-3 flex items-center gap-[10px]">
          <div
            className="w-[30px] h-[30px] rounded-full flex items-center justify-center font-bold text-[11px] flex-none"
            style={{ background: avatarGradient, color: avatarTextColor }}
          >
            {initials}
          </div>
          <div className="min-w-0">
            <div className="text-[13px] font-semibold text-[#111111] truncate">{claims.sub}</div>
            <div className="text-[11px] font-semibold truncate" style={{ color: roleColor }}>
              {roleLabel}
            </div>
          </div>
        </div>
        <button
          onClick={logout}
          className="flex items-center gap-2 w-full px-3 py-[10px] mt-0.5 rounded-[9px] text-[#666666] text-sm hover:bg-[#F5F5F5] hover:text-[#111111] transition-colors"
        >
          <LogOut size={15} />
          Sign Out
        </button>
      </div>
    </aside>
  );
}
