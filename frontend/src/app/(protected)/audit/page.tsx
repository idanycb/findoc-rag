'use client';

import React from 'react';
import { useAuth } from '@/context/AuthContext';
import { useDocuments } from '@/hooks/useDocuments';
import { MainHeader } from '@/features/layout/MainHeader';
import { AuditView } from '@/features/audit/AuditView';
import { LoadingIndicator } from '@/components/common/LoadingIndicator';

export default function AuditPage() {
  const { token } = useAuth();
  const { documents, loading } = useDocuments(token);

  return (
    <>
      <MainHeader
        title="Activity Log"
        subtitle="Chronological status history for indexed documents"
      />

      <section className="flex-1 overflow-y-auto bg-neutral-100/70 p-4 sm:p-6 lg:p-10">
        <div className="max-w-7xl mx-auto">
          {loading ? (
            <LoadingIndicator />
          ) : (
            <div className="animate-in fade-in slide-in-from-bottom-2 duration-500">
              <AuditView documents={documents} />
            </div>
          )}
        </div>
      </section>
    </>
  );
}
