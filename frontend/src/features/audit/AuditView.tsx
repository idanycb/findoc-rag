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
      <div className="overflow-x-auto">
        <table className="w-full min-w-160 border-collapse text-left">
          <thead className="border-b border-neutral-200 bg-neutral-100">
            <tr>
              <th className="p-4 text-[10px] font-black uppercase tracking-[0.2em] text-neutral-500 sm:p-6">
                Source Asset
              </th>
              <th className="p-4 text-[10px] font-black uppercase tracking-[0.2em] text-neutral-500 sm:p-6">
                Processing State
              </th>
              <th className="p-4 text-[10px] font-black uppercase tracking-[0.2em] text-neutral-500 sm:p-6">
                Activity Timestamp
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-neutral-200">
            {documents.length === 0 ? (
              <tr>
                <td
                  colSpan={3}
                  className="p-8 text-center text-sm font-semibold text-neutral-600 sm:p-12"
                >
                  No documents available yet. Upload a file to populate the
                  activity log.
                </td>
              </tr>
            ) : (
              documents.map((d) => (
                <tr
                  key={d.id}
                  className="group transition-all hover:bg-neutral-100"
                >
                  <td className="p-4 text-sm font-bold text-neutral-800 transition-colors group-hover:text-black sm:p-7">
                    {d.fileName}
                  </td>
                  <td className="p-4 sm:p-7">
                    <StatusBadge status={d.status} />
                  </td>
                  <td className="p-4 text-xs font-bold tabular-nums text-neutral-500 sm:p-7">
                    {new Date(d.uploadedAt).toLocaleString()}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </Card>
  );
};
