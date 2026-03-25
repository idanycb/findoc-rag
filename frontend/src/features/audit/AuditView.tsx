'use client';

import React from 'react';
import { Document } from '@/lib/types';
import Card from '@/components/ui/Card';
import StatusBadge from '@/features/vault/StatusBadge';

interface AuditViewProps {
  documents: Document[];
}

export const AuditView: React.FC<AuditViewProps> = ({ documents }) => {
  return (
    <Card className="p-0 overflow-hidden border-none shadow-2xl">
      <table className="w-full text-left border-collapse">
        <thead className="bg-slate-50 border-b border-slate-100">
          <tr>
            <th className="p-7 text-[10px] font-black uppercase text-slate-400 tracking-[0.2em]">
              Source Asset
            </th>
            <th className="p-7 text-[10px] font-black uppercase text-slate-400 tracking-[0.2em]">
              Archival Integrity
            </th>
            <th className="p-7 text-[10px] font-black uppercase text-slate-400 tracking-[0.2em]">
              Audit Timestamp
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-50">
          {documents.length === 0 ? (
            <tr>
              <td
                colSpan={3}
                className="p-12 text-center text-sm font-semibold text-slate-500"
              >
                No documents available yet. Upload a file to populate the audit
                log.
              </td>
            </tr>
          ) : (
            documents.map((d) => (
              <tr
                key={d.id}
                className="hover:bg-indigo-50/20 transition-all group"
              >
                <td className="p-10 font-bold text-slate-700 text-sm group-hover:text-indigo-600 transition-colors">
                  {d.fileName}
                </td>
                <td className="p-10">
                  <StatusBadge status={d.status} />
                </td>
                <td className="p-10 text-xs text-slate-400 font-bold tabular-nums">
                  {new Date(d.uploadedAt).toLocaleString()}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </Card>
  );
};
