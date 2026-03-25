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
        title="Audit"
        subtitle="Complete archival history and compliance log"
      />

      <section className="flex-1 overflow-y-auto p-12 bg-[#F8FAFC]/50">
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
