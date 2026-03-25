'use client';

import { useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useDocuments } from '@/hooks/useDocuments';
import { MainHeader } from '@/features/layout/MainHeader';
import DocumentsView from '@/features/vault/DocumentsView';
import { UploadOverlay } from '@/features/upload/UploadOverlay';
import { InsightsOverlay } from '@/features/insights/InsightsOverlay';
import { LoadingIndicator } from '@/components/common/LoadingIndicator';
import { Document } from '@/lib/types';
import { apiCall } from '@/lib/api';

export default function DashboardPage() {
  const { token } = useAuth();
  const { documents, loading, refetch } = useDocuments(token);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const handleUploadComplete = () => {
    refetch();
    setIsUploadOpen(false);
  };

  const handleDeleteDocument = async (doc: Document) => {
    if (!confirm(`Delete "${doc.fileName}"? This action cannot be undone.`)) {
      return;
    }

    try {
      setDeletingId(doc.id);
      await apiCall(`/documents/${doc.id}`, { method: 'DELETE' }, token);

      if (selectedDoc?.id === doc.id) {
        setSelectedDoc(null);
      }

      await refetch();
    } catch (err) {
      console.error(`Failed to delete document ${doc.id}:`, err);
      alert(err instanceof Error ? err.message : 'Failed to delete document');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <>
      <MainHeader
        title="Dashboard"
        onUploadClick={() => setIsUploadOpen(true)}
      />

      <section className="flex-1 overflow-y-auto p-12 bg-[#F8FAFC]/50">
        <div className="max-w-7xl mx-auto">
          {loading ? (
            <LoadingIndicator />
          ) : (
            <div className="animate-in fade-in slide-in-from-bottom-2 duration-500">
              <DocumentsView
                documents={documents}
                onSelectDoc={(doc) => setSelectedDoc(doc as Document)}
              />
            </div>
          )}
        </div>
      </section>

      {isUploadOpen && (
        <UploadOverlay
          token={token}
          onClose={() => setIsUploadOpen(false)}
          onComplete={handleUploadComplete}
        />
      )}

      {selectedDoc && (
        <InsightsOverlay
          doc={selectedDoc}
          token={token}
          onDeleteDoc={handleDeleteDocument}
          deletingId={deletingId}
          onClose={() => setSelectedDoc(null)}
        />
      )}
    </>
  );
}
