'use client';

import { useState, useEffect, useCallback, useTransition } from 'react';
import { Plus, Pencil, Trash2, X, Check } from 'lucide-react';
import { apiCall, ApiError } from '@/shared/lib/api';
import { formatDate } from '@/shared/lib/auth';
import type { TeamView, UserView } from '@/shared/types';
import { useAuth } from '@/context/AuthContext';
import { useRequireRole } from '@/shared/hooks/useRequireRole';

function teamInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || 'T';
}

export function TeamsClient() {
  const { token } = useAuth();
  const { isCheckingAccess } = useRequireRole(['SUPER_ADMIN']);
  const [teams, setTeams] = useState<TeamView[]>([]);
  const [users, setUsers] = useState<UserView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [isPending, startTransition] = useTransition();

  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('');
  const [createError, setCreateError] = useState('');

  const [editId, setEditId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editError, setEditError] = useState('');

  const fetchAll = useCallback(async () => {
    if (!token) return;
    try {
      const [teamsData, usersData] = await Promise.all([
        apiCall<TeamView[]>('/teams', undefined, token),
        apiCall<UserView[]>('/users', undefined, token),
      ]);
      setTeams(teamsData ?? []);
      setUsers(usersData ?? []);
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const memberCount = (teamId: string) =>
    users.filter((u) => u.teamId === teamId).length;

  const handleCreate = () => {
    if (!newName.trim()) return;
    setCreateError('');
    startTransition(async () => {
      try {
        const team = await apiCall<TeamView>(
          '/teams',
          { method: 'POST', body: JSON.stringify({ name: newName.trim() }) },
          token
        );
        setTeams((prev) => [...prev, team]);
        setNewName('');
        setShowCreate(false);
      } catch (err) {
        setCreateError(err instanceof ApiError ? err.message : 'Create failed.');
      }
    });
  };

  const handleRename = (id: string) => {
    if (!editName.trim()) return;
    setEditError('');
    startTransition(async () => {
      try {
        const updated = await apiCall<TeamView>(
          `/teams/${id}`,
          { method: 'PUT', body: JSON.stringify({ name: editName.trim() }) },
          token
        );
        setTeams((prev) => prev.map((t) => (t.id === id ? updated : t)));
        setEditId(null);
      } catch (err) {
        setEditError(err instanceof ApiError ? err.message : 'Rename failed.');
      }
    });
  };

  const handleDelete = (id: string, name: string) => {
    if (!confirm(`Delete team "${name}"? All members must be removed first.`)) return;
    startTransition(async () => {
      try {
        await apiCall(`/teams/${id}`, { method: 'DELETE' }, token);
        setTeams((prev) => prev.filter((t) => t.id !== id));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Delete failed.');
      }
    });
  };

  if (isCheckingAccess) {
    return <div className="flex-1 flex items-center justify-center text-[#888888]">Loading…</div>;
  }

  return (
    <>
      {/* Header */}
      <div className="flex items-center justify-between bg-white px-5 py-4 border-b border-[#EBEBEB] md:px-7 md:py-5">
        <div>
          <h1 className="text-[22px] font-bold text-[#111111] tracking-[-.01em]">Teams</h1>
          <p className="text-[11px] uppercase tracking-[.12em] text-[#AAAAAA] mt-0.5">
            Every member and document belongs to a team.
          </p>
        </div>
        <button
          onClick={() => { setShowCreate(true); setCreateError(''); }}
          className="flex items-center gap-2 bg-[#111111] text-white font-semibold text-[13.5px] px-4 h-9.5 rounded-lg hover:bg-[#333333] transition-colors"
        >
          <Plus size={16} strokeWidth={2.2} />
          New team
        </button>
      </div>

      <div className="px-5 py-4 md:px-7 md:py-7 flex flex-col gap-4">
        {error && (
          <div className="text-[13px] text-[#B91C1C] bg-[#FEF2F2] border border-[#FECACA] rounded-[9px] px-4 py-3">
            {error}
          </div>
        )}

        {/* Create inline form */}
        {showCreate && (
          <div className="flex flex-col gap-3 bg-white rounded-xl px-4.5 py-4 shadow-[0_1px_4px_rgba(0,0,0,.06)] sm:flex-row sm:items-center">
            <span className="text-[13px] font-semibold text-[#888888] whitespace-nowrap">
              Create team
            </span>
            <input
              autoFocus
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleCreate();
                if (e.key === 'Escape') setShowCreate(false);
              }}
              placeholder="Team name…"
              className="flex-1 bg-[#F7F7F7] border border-[#E8E8E8] rounded-lg px-3.25 h-10 text-sm text-[#111111] placeholder:text-[#BBBBBB] outline-hidden focus:border-[#111111]"
            />
            {createError && (
              <span className="text-[12px] text-[#B91C1C]">{createError}</span>
            )}
            <button
              onClick={handleCreate}
              disabled={isPending}
              className="h-10 rounded-lg border border-[#E5E5E5] bg-white px-4 text-sm font-semibold text-[#333333] hover:bg-[#F5F5F5] transition-colors disabled:opacity-60"
            >
              Add
            </button>
            <button
              onClick={() => setShowCreate(false)}
              className="w-10 h-10 rounded-lg border border-[#E5E5E5] flex items-center justify-center hover:bg-[#F5F5F5] transition-colors"
            >
              <X size={15} className="text-[#666666]" />
            </button>
          </div>
        )}

        {loading ? (
          <div className="text-[14px] text-[#888888] text-center py-8">Loading…</div>
        ) : teams.length === 0 ? (
          <div className="text-[14px] text-[#888888] text-center py-8">
            No teams yet. Create one above.
          </div>
        ) : (
          <>
            {/* Desktop table */}
            <div className="hidden sm:block bg-white rounded-[14px] overflow-hidden shadow-[0_1px_4px_rgba(0,0,0,.06)]">
              <div className="grid grid-cols-[1fr_100px_200px_120px] px-5 py-3.25 border-b border-[#F5F5F5] text-[11px] font-bold uppercase text-[#AAAAAA] tracking-widest">
                <span>TEAM NAME</span>
                <span>MEMBERS</span>
                <span>CREATED</span>
                <span className="text-right">ACTIONS</span>
              </div>
              {teams.map((team) => {
                const isEditing = editId === team.id;
                const count = memberCount(team.id);
                return (
                  <div
                    key={team.id}
                    className="grid grid-cols-[1fr_100px_200px_120px] px-5 py-4 border-b border-[#F8F8F8] last:border-0 items-center hover:bg-[#FAFAFA] transition-colors"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="w-9 h-9 rounded-[10px] bg-[#EFF6FF] flex items-center justify-center flex-none text-sm font-bold text-[#3B82F6]">
                        {teamInitial(team.name)}
                      </div>
                      {isEditing ? (
                        <div className="flex items-center gap-2 flex-1">
                          <input
                            autoFocus
                            value={editName}
                            onChange={(e) => setEditName(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') handleRename(team.id);
                              if (e.key === 'Escape') setEditId(null);
                            }}
                            className="flex-1 bg-transparent text-[14px] text-[#111111] outline-hidden border-b border-[#111111]"
                          />
                          {editError && (
                            <span className="text-[12px] text-[#B91C1C]">{editError}</span>
                          )}
                          <button
                            onClick={() => handleRename(team.id)}
                            disabled={isPending}
                            className="p-1 rounded hover:bg-[#F5F5F5]"
                          >
                            <Check size={14} className="text-[#22C55E]" />
                          </button>
                          <button onClick={() => setEditId(null)} className="p-1 rounded hover:bg-[#F5F5F5]">
                            <X size={14} className="text-[#666666]" />
                          </button>
                        </div>
                      ) : (
                        <div>
                          <span className="text-[15px] font-semibold text-[#111111] truncate">
                            {team.name}
                          </span>
                          {count === 0 && (
                            <div className="text-[11.5px] text-[#AAAAAA]">0 members - deletable</div>
                          )}
                        </div>
                      )}
                    </div>
                    <span className={count === 0 ? 'text-sm text-[#CCCCCC]' : 'text-sm text-[#555555]'}>{count}</span>
                    <span className="text-[13px] text-[#999999]">{formatDate(team.createdAt)}</span>
                    {!isEditing && (
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => { setEditId(team.id); setEditName(team.name); setEditError(''); }}
                          className="w-8 h-8 border border-[#E5E5E5] rounded-lg bg-white flex items-center justify-center hover:bg-[#F5F5F5] transition-colors"
                        >
                          <Pencil size={14} className="text-[#666666]" />
                        </button>
                        <button
                          onClick={() => handleDelete(team.id, team.name)}
                          disabled={isPending}
                          className="w-8 h-8 border border-[#FECACA] rounded-lg bg-[#FEF2F2] flex items-center justify-center hover:bg-[#FEE2E2] transition-colors disabled:opacity-60"
                        >
                          <Trash2 size={14} className="text-[#EF4444]" />
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            {/* Mobile cards */}
            <div className="sm:hidden flex flex-col gap-3">
              {teams.map((team) => (
                <div
                  key={team.id}
                  className="bg-white rounded-[14px] p-3.75 shadow-[0_1px_3px_rgba(0,0,0,.06)]"
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-[11px] bg-[#EFF6FF] flex items-center justify-center text-base font-bold text-[#3B82F6]">
                        {teamInitial(team.name)}
                      </div>
                      <div>
                        <div className="text-[14.5px] font-semibold text-[#111111]">{team.name}</div>
                        <div className="text-xs text-[#AAAAAA] mt-0.5">
                          {memberCount(team.id)} members · {formatDate(team.createdAt)}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => { setEditId(team.id); setEditName(team.name); setEditError(''); }}
                        className="p-2 rounded-[7px] hover:bg-[#F5F5F5]"
                      >
                        <Pencil size={14} className="text-[#666666]" />
                      </button>
                      <button
                        onClick={() => handleDelete(team.id, team.name)}
                        className="p-2 rounded-[7px] hover:bg-[#FEF2F2]"
                      >
                        <Trash2 size={14} className="text-[#EF4444]" />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
        <div className="flex items-center gap-2.25 rounded-[9px] border border-[#FECACA] bg-[#FEF3F2] px-3.5 py-2.75">
          <Trash2 size={14} className="flex-none text-[#DC2626]" />
          <div className="text-[12.5px] text-[#991B1B]">
            Teams with members cannot be deleted. Reassign or remove all members before deleting a team.
          </div>
        </div>
      </div>
    </>
  );
}
