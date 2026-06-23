'use client';

import { useState, useEffect, useCallback, useTransition } from 'react';
import { Plus, Trash2, ChevronDown, X, Info } from 'lucide-react';
import { apiCall, ApiError } from '@/shared/lib/api';
import { getUserInitials } from '@/shared/lib/auth';
import type { UserView, TeamView, UserRole } from '@/shared/types';
import { useAuth } from '@/context/AuthContext';
import { useRequireRole } from '@/shared/hooks/useRequireRole';

const ROLE_COLORS: Record<UserRole, { text: string; bg: string; border: string }> = {
  SUPER_ADMIN: { text: '#92400E', bg: '#FFFBEB', border: '#FDE68A' },
  ADMIN: { text: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE' },
  MEMBER: { text: '#666666', bg: '#F5F5F5', border: '#E5E5E5' },
};

function RoleBadge({ role }: { role: UserRole }) {
  const cfg = ROLE_COLORS[role];
  return (
    <span
      className="inline-flex rounded-md border px-[10px] py-1 text-[11px] font-bold"
      style={{ color: cfg.text, background: cfg.bg, borderColor: cfg.border }}
    >
      {role}
    </span>
  );
}

interface CreateUserForm {
  username: string;
  password: string;
  role: 'ADMIN' | 'MEMBER';
  teamId: string;
}

export function UsersClient() {
  const { token, claims } = useAuth();
  const { isCheckingAccess } = useRequireRole(['SUPER_ADMIN', 'ADMIN']);
  const isSuperAdmin = claims?.role === 'SUPER_ADMIN';
  const isAdmin = claims?.role === 'ADMIN';

  const [users, setUsers] = useState<UserView[]>([]);
  const [teams, setTeams] = useState<TeamView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isPending, startTransition] = useTransition();

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateUserForm>({
    username: '',
    password: '',
    role: 'MEMBER',
    teamId: '',
  });
  const [createError, setCreateError] = useState('');

  const [roleFilter, setRoleFilter] = useState<string>('All roles');
  const [teamFilter, setTeamFilter] = useState<string>('All teams');

  const fetchAll = useCallback(async () => {
    if (!token) return;
    try {
      const [usersData, teamsData] = await Promise.all([
        apiCall<UserView[]>('/users', undefined, token),
        isSuperAdmin ? apiCall<TeamView[]>('/teams', undefined, token) : Promise.resolve<TeamView[]>([]),
      ]);
      setUsers(usersData ?? []);
      setTeams(teamsData ?? []);
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [token, isSuperAdmin]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const handleCreate = () => {
    setCreateError('');
    startTransition(async () => {
      try {
        const body: Record<string, string> = {
          username: form.username,
          password: form.password,
        };
        if (isSuperAdmin) {
          body.role = form.role;
          body.teamId = form.teamId;
        }
        const user = await apiCall<UserView>(
          '/users',
          { method: 'POST', body: JSON.stringify(body) },
          token
        );
        setUsers((prev) => [...prev, user]);
        setForm({ username: '', password: '', role: 'MEMBER', teamId: '' });
        setShowCreate(false);
      } catch (err) {
        setCreateError(err instanceof ApiError ? err.message : 'Create failed.');
      }
    });
  };

  const handleRoleChange = (userId: string, newRole: 'ADMIN' | 'MEMBER') => {
    startTransition(async () => {
      try {
        const updated = await apiCall<UserView>(
          `/users/${userId}/role`,
          { method: 'PATCH', body: JSON.stringify({ role: newRole }) },
          token
        );
        setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Role change failed.');
      }
    });
  };

  const handleDelete = (userId: string, username: string) => {
    if (!confirm(`Delete user "${username}"?`)) return;
    startTransition(async () => {
      try {
        await apiCall(`/users/${userId}`, { method: 'DELETE' }, token);
        setUsers((prev) => prev.filter((u) => u.id !== userId));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Delete failed.');
      }
    });
  };

  const teamName = (teamId: string | null) => {
    if (!teamId) return '—';
    return teams.find((t) => t.id === teamId)?.name ?? teamId.slice(0, 8) + '…';
  };

  const canDeleteUser = (u: UserView) => {
    if (u.role === 'SUPER_ADMIN') return false;
    if (u.id === claims?.userId) return false;
    if (isAdmin && u.role === 'ADMIN') return false;
    return true;
  };

  const canChangeRole = (u: UserView) => {
    if (u.role === 'SUPER_ADMIN') return false;
    if (u.id === claims?.userId) return false;
    if (isAdmin && u.role !== 'MEMBER') return false;
    return true;
  };

  const roleOptionsFor = (u: UserView): Array<'ADMIN' | 'MEMBER'> => {
    if (isSuperAdmin) return ['MEMBER', 'ADMIN'];
    if (isAdmin && u.role === 'MEMBER') return ['MEMBER', 'ADMIN'];
    return [u.role === 'ADMIN' ? 'ADMIN' : 'MEMBER'];
  };

  const roleFilters = ['All roles', 'ADMIN', 'MEMBER', ...(isSuperAdmin ? ['SUPER_ADMIN'] : [])];
  const teamFilters = ['All teams', ...teams.map((team) => team.name)];
  const filtered = users.filter((u) => {
    const matchesRole = roleFilter === 'All roles' || u.role === roleFilter;
    const matchesTeam = !isSuperAdmin || teamFilter === 'All teams' || teamName(u.teamId) === teamFilter;
    return matchesRole && matchesTeam;
  });

  if (isCheckingAccess) {
    return <div className="flex-1 flex items-center justify-center text-[#888888]">Loading…</div>;
  }

  return (
    <>
      {/* Header */}
      <div className="flex items-center justify-between bg-white px-5 py-4 border-b border-[#EBEBEB] md:px-7 md:py-5">
        <div>
          <h1 className="text-[22px] font-bold text-[#111111] tracking-[-.01em]">Users</h1>
          <p className="text-[11px] uppercase tracking-[.12em] text-[#AAAAAA] mt-[2px]">
            {isSuperAdmin ? 'All users across every team.' : 'Members of your team.'}
          </p>
        </div>
        <button
          onClick={() => { setShowCreate(true); setCreateError(''); }}
          className="flex items-center gap-2 bg-[#111111] text-white font-semibold text-[13.5px] px-4 h-[38px] rounded-lg hover:bg-[#333333] transition-colors"
        >
          <Plus size={16} strokeWidth={2.2} />
          Create user
        </button>
      </div>

      <div className="px-5 py-4 md:px-7 md:py-7 flex flex-col gap-4">
        {/* Info banner */}
        {isSuperAdmin && (
          <div className="flex items-start gap-[9px] bg-[#EFF6FF] border border-[#BFDBFE] rounded-[10px] px-3 py-[11px]">
            <Info size={14} className="text-[#1D4ED8] flex-none mt-[1px]" />
            <p className="text-[11.5px] text-[#1E40AF] leading-[1.5]">
              Super admin sees all users. Admins see only their own team.
            </p>
          </div>
        )}

        {error && (
          <div className="text-[13px] text-[#B91C1C] bg-[#FEF2F2] border border-[#FECACA] rounded-[9px] px-4 py-3">
            {error}
          </div>
        )}

        {/* Create form */}
        {showCreate && (
          <div className="bg-white rounded-[12px] p-5 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="flex items-center justify-between mb-4">
              <span className="text-[11px] font-bold uppercase text-[#AAAAAA] tracking-[.1em]">
                CREATE USER
              </span>
              <button onClick={() => setShowCreate(false)}>
                <X size={16} className="text-[#888888] hover:text-[#111111]" />
              </button>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="text-[12px] font-medium text-[#444444]">Username</label>
                <input
                  value={form.username}
                  onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
                  placeholder="bob.member"
                  className="mt-1 w-full bg-[#F7F7F7] border border-[#E8E8E8] rounded-[9px] px-3 h-[42px] text-[14px] text-[#111111] placeholder:text-[#BBBBBB] outline-none focus:border-[#111111] transition-colors"
                />
              </div>
              <div>
                <label className="text-[12px] font-medium text-[#444444]">Password</label>
                <input
                  type="password"
                  value={form.password}
                  onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
                  placeholder="Min. 8 characters"
                  className="mt-1 w-full bg-[#F7F7F7] border border-[#E8E8E8] rounded-[9px] px-3 h-[42px] text-[14px] text-[#111111] placeholder:text-[#BBBBBB] outline-none focus:border-[#111111] transition-colors"
                />
              </div>
              {isSuperAdmin && (
                <>
                  <div>
                    <label className="text-[12px] font-medium text-[#444444]">Role</label>
                    <div className="relative mt-1">
                      <select
                        value={form.role}
                        onChange={(e) => setForm((f) => ({ ...f, role: e.target.value as 'ADMIN' | 'MEMBER' }))}
                        className="w-full bg-[#F7F7F7] border border-[#E8E8E8] rounded-[9px] px-3 h-[42px] text-[14px] text-[#111111] outline-none appearance-none focus:border-[#111111] transition-colors"
                      >
                        <option value="MEMBER">MEMBER</option>
                        <option value="ADMIN">ADMIN</option>
                      </select>
                      <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#AAAAAA] pointer-events-none" />
                    </div>
                  </div>
                  <div>
                    <label className="text-[12px] font-medium text-[#444444]">Team</label>
                    <div className="relative mt-1">
                      <select
                        value={form.teamId}
                        onChange={(e) => setForm((f) => ({ ...f, teamId: e.target.value }))}
                        className="w-full bg-[#F7F7F7] border border-[#E8E8E8] rounded-[9px] px-3 h-[42px] text-[14px] text-[#111111] outline-none appearance-none focus:border-[#111111] transition-colors"
                      >
                        <option value="">Select team…</option>
                        {teams.map((t) => (
                          <option key={t.id} value={t.id}>
                            {t.name}
                          </option>
                        ))}
                      </select>
                      <ChevronDown size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-[#AAAAAA] pointer-events-none" />
                    </div>
                  </div>
                </>
              )}
            </div>
            {createError && (
              <p className="text-[12px] text-[#B91C1C] mt-3">{createError}</p>
            )}
            <div className="flex justify-end mt-4">
              <button
                onClick={handleCreate}
                disabled={isPending || !form.username || form.password.length < 8 || (isSuperAdmin && !form.teamId)}
                className="bg-[#111111] text-white font-semibold text-[13px] px-5 h-[38px] rounded-[9px] hover:bg-[#333333] transition-colors disabled:opacity-60"
              >
                {isPending ? 'Creating…' : 'Create user'}
              </button>
            </div>
          </div>
        )}

        {/* Filters */}
        <div className="flex gap-[10px] flex-wrap">
          {roleFilters.map((f) => (
            <button
              key={f}
              onClick={() => setRoleFilter(f)}
              className={`inline-flex items-center gap-[7px] text-[13px] font-semibold px-[14px] py-[7px] rounded-[8px] border transition-colors ${
                roleFilter === f
                  ? 'text-white bg-[#111111] border-[#111111]'
                  : 'text-[#666666] bg-white border-[#E5E5E5] hover:text-[#111111]'
              }`}
            >
              {f}
            </button>
          ))}
        </div>
        {isSuperAdmin && teams.length > 0 && (
          <div className="flex gap-2 flex-wrap">
            {teamFilters.map((f) => (
              <button
                key={f}
                onClick={() => setTeamFilter(f)}
                className={`inline-flex items-center gap-[7px] text-[13px] px-[14px] py-[7px] rounded-[8px] border transition-colors ${
                  teamFilter === f
                    ? 'text-white bg-[#111111] border-[#111111] font-semibold'
                    : 'text-[#666666] bg-white border-[#E5E5E5] hover:text-[#111111]'
                }`}
              >
                {f}
              </button>
            ))}
          </div>
        )}

        {loading ? (
          <div className="text-[14px] text-[#888888] text-center py-8">Loading…</div>
        ) : filtered.length === 0 ? (
          <div className="text-[14px] text-[#888888] text-center py-8">No users found.</div>
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden sm:block bg-white rounded-[14px] overflow-hidden shadow-[0_1px_4px_rgba(0,0,0,.06)]">
              <div
                className={`grid px-5 py-[13px] border-b border-[#F5F5F5] text-[11px] font-bold uppercase text-[#AAAAAA] tracking-[.1em] ${isSuperAdmin ? 'grid-cols-[1fr_160px_180px_140px]' : 'grid-cols-[1fr_160px_140px]'}`}
              >
                <span>USERNAME</span>
                <span>ROLE</span>
                {isSuperAdmin && <span>TEAM</span>}
                <span />
              </div>
              {filtered.map((user) => (
                <div
                  key={user.id}
                  className={`grid px-5 py-[14px] border-b border-[#F8F8F8] last:border-0 items-center hover:bg-[#FAFAFA] transition-colors ${isSuperAdmin ? 'grid-cols-[1fr_160px_180px_140px]' : 'grid-cols-[1fr_160px_140px]'}`}
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className="w-8 h-8 rounded-full flex items-center justify-center font-semibold text-[12px] flex-none"
                      style={{
                        background: user.role === 'SUPER_ADMIN'
                          ? '#FDE68A'
                          : user.role === 'ADMIN'
                            ? '#EFF6FF'
                            : '#F5F5F5',
                        color: user.role === 'SUPER_ADMIN'
                          ? '#92400E'
                          : user.role === 'ADMIN'
                            ? '#3B82F6'
                            : '#777777',
                      }}
                    >
                      {getUserInitials(user.username)}
                    </div>
                    <span className="text-[14.5px] font-semibold text-[#111111] truncate">
                      {user.username}
                    </span>
                  </div>
                  <div>
                    {canChangeRole(user) && (isAdmin || isSuperAdmin) ? (
                      <div className="relative inline-block">
                        <select
                          value={user.role}
                          onChange={(e) =>
                            handleRoleChange(user.id, e.target.value as 'ADMIN' | 'MEMBER')
                          }
                          disabled={isPending}
                          className="appearance-none bg-transparent text-[11.5px] font-bold pr-4 outline-none cursor-pointer"
                          style={{ color: ROLE_COLORS[user.role]?.text }}
                        >
                          {roleOptionsFor(user).map((role) => (
                            <option key={role} value={role}>
                              {role}
                            </option>
                          ))}
                        </select>
                        <ChevronDown
                          size={12}
                          className="absolute right-0 top-1/2 -translate-y-1/2 pointer-events-none"
                          style={{ color: ROLE_COLORS[user.role]?.text }}
                        />
                      </div>
                    ) : (
                      <RoleBadge role={user.role} />
                    )}
                  </div>
                  {isSuperAdmin && (
                    <span className="text-sm text-[#555555] truncate">{teamName(user.teamId)}</span>
                  )}
                  <div className="flex items-center justify-end">
                    {canDeleteUser(user) && (
                      <button
                        onClick={() => handleDelete(user.id, user.username)}
                        disabled={isPending}
                        className="w-[30px] h-[30px] border border-[#FECACA] rounded-[7px] bg-[#FEF2F2] flex items-center justify-center hover:bg-[#FEE2E2] transition-colors disabled:opacity-60"
                      >
                        <Trash2 size={13} className="text-[#EF4444]" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            {/* Mobile cards */}
            <div className="sm:hidden flex flex-col gap-3">
              {filtered.map((user) => (
                <div
                  key={user.id}
                  className="bg-white rounded-[13px] p-[14px] shadow-[0_1px_3px_rgba(0,0,0,.06)] flex items-center gap-3"
                >
                  <div
                    className="w-[38px] h-[38px] rounded-full flex items-center justify-center font-semibold text-[13px] flex-none"
                    style={{
                      background: user.role === 'SUPER_ADMIN'
                        ? '#FDE68A'
                        : user.role === 'ADMIN'
                          ? '#EFF6FF'
                          : '#F5F5F5',
                      color: user.role === 'SUPER_ADMIN'
                        ? '#92400E'
                        : user.role === 'ADMIN'
                          ? '#3B82F6'
                          : '#777777',
                    }}
                  >
                    {getUserInitials(user.username)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold text-[#111111] truncate">
                      {user.username}
                    </div>
                    <div className="flex items-center gap-2 mt-[4px]">
                      {canChangeRole(user) && (isAdmin || isSuperAdmin) ? (
                        <div className="relative inline-block">
                          <select
                            value={user.role}
                            onChange={(e) =>
                              handleRoleChange(user.id, e.target.value as 'ADMIN' | 'MEMBER')
                            }
                            disabled={isPending}
                            className="appearance-none rounded-md border bg-white py-1 pl-[10px] pr-6 text-[11px] font-bold outline-none disabled:opacity-60"
                            style={{
                              color: ROLE_COLORS[user.role]?.text,
                              borderColor: ROLE_COLORS[user.role]?.border,
                            }}
                          >
                            {roleOptionsFor(user).map((role) => (
                              <option key={role} value={role}>
                                {role}
                              </option>
                            ))}
                          </select>
                          <ChevronDown
                            size={12}
                            className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none"
                            style={{ color: ROLE_COLORS[user.role]?.text }}
                          />
                        </div>
                      ) : (
                        <RoleBadge role={user.role} />
                      )}
                      {isSuperAdmin && user.teamId && (
                        <span className="text-xs text-[#AAAAAA]">{teamName(user.teamId)}</span>
                      )}
                    </div>
                  </div>
                  {canDeleteUser(user) && (
                    <button
                      onClick={() => handleDelete(user.id, user.username)}
                      className="p-2 rounded-[7px] hover:bg-[#FEF2F2]"
                    >
                      <Trash2 size={15} className="text-[#EF4444]" />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </>
  );
}
