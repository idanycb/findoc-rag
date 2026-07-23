'use client';

import { useState, useRef } from 'react';
import Link from 'next/link';
import { ChevronRight, FileText, Landmark, Search, Trash2, Upload } from 'lucide-react';
import { apiCall, ApiError } from '@/shared/lib/api';
import { uploadDocument } from '@/shared/lib/documents';
import { formatBytes, formatDate } from '@/shared/lib/auth';
import type { DocumentSummaryResponse } from '@/shared/types';
import { StatusBadge } from './StatusBadge';
import { UploadZone } from './UploadZone';
import { useAuth } from '@/context/AuthContext';
import { useDocuments } from '@/shared/hooks/useDocuments';
import { useRequireRole } from '@/shared/hooks/useRequireRole';

const VAULT_ROLES = ['ADMIN', 'MEMBER'] as const;

const FILE_COLORS: Record<string, string> = {
  'application/pdf': '#F08A78',
  'text/plain': '#6f9bff',
  'text/markdown': '#5BD39B',
  default: '#9398A1',
};

function fileColor(contentType: string) {
  return FILE_COLORS[contentType] ?? FILE_COLORS.default;
}

function documentActionLabel(status: DocumentSummaryResponse['status']) {
  return status === 'FAILED' ? 'Retry analysis' : 'View Details';
}

function isEdgarDocument(doc: DocumentSummaryResponse) {
  return doc.source === 'EDGAR';
}

function filingSummary(doc: DocumentSummaryResponse) {
  return [doc.ticker, doc.formType, doc.fiscalPeriod].filter(Boolean).join(' · ');
}

export function VaultClient() {
  const { token } = useAuth();
  const { isCheckingAccess } = useRequireRole([...VAULT_ROLES]);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const {
    documents: docs,
    setDocuments: setDocs,
    loading,
    error,
    setError,
    refetch: fetchDocs,
  } = useDocuments({ enabled: !isCheckingAccess, pollProcessing: true });
  const [search, setSearch] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  const handleUpload = async (file: File) => {
    setUploadError('');
    setUploading(true);
    try {
      await uploadDocument(file, token);
      await fetchDocs();
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.message : 'Upload failed.');
    } finally {
      setUploading(false);
    }
  };

  const handleHeaderFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      void handleUpload(file);
      e.target.value = '';
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Delete this document? This cannot be undone.')) return;
    try {
      await apiCall(`/documents/${id}`, { method: 'DELETE' }, token);
      setDocs((prev) => prev.filter((d) => d.id !== id));
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
    }
  };

  const filtered = docs.filter((d) => {
    const haystack = [
      d.fileName,
      d.ticker,
      d.companyName,
      d.formType,
      d.accessionNumber,
    ].filter(Boolean).join(' ');
    return haystack.toLowerCase().includes(search.toLowerCase());
  });

  const total = docs.length;
  const completed = docs.filter((d) => d.status === 'COMPLETED').length;
  const inFlight = docs.filter((d) => d.status === 'PROCESSING' || d.status === 'PENDING').length;
  const failed = docs.filter((d) => d.status === 'FAILED').length;

  if (isCheckingAccess) {
    return <div className="flex-1 flex items-center justify-center text-[#888888]">Loading…</div>;
  }

  return (
    <>
      {/* Header */}
      <div className="flex items-center justify-between bg-white px-5 py-4 border-b border-[#EBEBEB] md:px-7 md:py-5">
        <div>
          <h1 className="text-[22px] font-bold tracking-[-.01em] text-[#111111]">Dashboard</h1>
          <p className="mt-0.5 text-[11px] uppercase tracking-[.12em] text-[#AAAAAA]">
            Team Document Vault
          </p>
        </div>
        <div className="hidden sm:flex items-center gap-3">
          <div className="flex h-[38px] w-[220px] items-center gap-2 rounded-lg border border-[#E8E8E8] bg-[#F5F5F5] px-[14px]">
            <Search size={15} className="text-[#AAAAAA]" />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search documents…"
              className="flex-1 bg-transparent text-sm text-[#111111] placeholder:text-[#BBBBBB] outline-none"
            />
          </div>
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="flex h-[38px] items-center gap-2 rounded-lg bg-[#111111] px-4 text-sm font-semibold text-white disabled:opacity-60"
          >
            <Upload size={15} strokeWidth={2.2} />
            {uploading ? 'Uploading…' : 'Upload Document'}
          </button>
        </div>
      </div>

      <div className="px-5 py-4 md:px-7 md:py-7 flex flex-col gap-5 flex-1 overflow-auto">
        <input
          ref={fileInputRef}
          type="file"
          accept=".pdf,.txt,.md,.docx,application/pdf,text/plain,text/markdown,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
          className="hidden"
          onChange={handleHeaderFileChange}
        />
        {/* Search — mobile */}
        <div className="sm:hidden flex items-center gap-2 bg-white border border-[#E8E8E8] rounded-[9px] px-[14px] h-10">
          <Search size={14} className="text-[#BBBBBB]" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search documents…"
            className="flex-1 bg-transparent text-sm text-[#111111] placeholder:text-[#BBBBBB] outline-none"
          />
        </div>

        <button
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          className="sm:hidden flex h-11 items-center justify-center gap-2 rounded-[10px] bg-[#111111] text-sm font-semibold text-white disabled:opacity-60"
        >
          <Upload size={15} strokeWidth={2.2} />
          {uploading ? 'Uploading…' : 'Upload Document'}
        </button>

        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            ['Total', total, 'text-[#111111]'],
            ['Completed', completed, 'text-[#111111]'],
            ['In progress', inFlight, 'text-[#555555]'],
            ['Failed', failed, 'text-[#B91C1C]'],
          ].map(([label, value, color]) => (
            <div key={label} className="rounded-[12px] bg-white p-4 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
              <div className="text-xs text-[#888888]">{label}</div>
              <div className={`mt-1 text-2xl font-bold tracking-tight ${color}`}>{value}</div>
            </div>
          ))}
        </div>

        {/* Upload zone */}
        {uploadError && (
          <div className="text-[13px] text-[#B91C1C] bg-[#FEF2F2] border border-[#FECACA] rounded-[9px] px-4 py-3">
            {uploadError}
          </div>
        )}
        <UploadZone onUpload={handleUpload} uploading={uploading} />

        {error && (
          <div className="text-[13px] text-[#B91C1C] bg-[#FEF2F2] border border-[#FECACA] rounded-[9px] px-4 py-3">
            {error}
          </div>
        )}

        {loading ? (
          <div className="text-[14px] text-[#888888] text-center py-8">Loading…</div>
        ) : filtered.length === 0 ? (
          <div className="text-[14px] text-[#888888] text-center py-8">
            {search ? 'No matching documents.' : 'No documents yet. Upload one above.'}
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
            {filtered.map((doc) => {
              const color = fileColor(doc.contentType);
              const edgarDocument = isEdgarDocument(doc);
              return (
                <article
                  key={doc.id}
                  className="group rounded-2xl bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,.06)]"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div
                      className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#F5F5F5]"
                    >
                      {edgarDocument ? (
                        <Landmark size={20} className="text-[#111111]" strokeWidth={1.8} />
                      ) : (
                        <FileText size={20} style={{ color }} strokeWidth={1.8} />
                      )}
                    </div>
                    <div className="flex flex-col items-end gap-2">
                      <StatusBadge status={doc.status} />
                      {edgarDocument && (
                        <span className="rounded-full bg-[#F5F5F5] px-2.5 py-1 text-[10px] font-bold uppercase tracking-[.08em] text-[#555555]">
                          EDGAR
                        </span>
                      )}
                    </div>
                  </div>
                  <Link href={`/vault/documents/${doc.id}`} className="mt-[14px] block">
                    <h2 className="truncate text-[15px] font-bold text-[#111111]">
                      {doc.fileName}
                    </h2>
                    {edgarDocument && filingSummary(doc) && (
                      <p className="mt-[3px] truncate text-[13px] font-semibold text-[#555555]">
                        {filingSummary(doc)}
                      </p>
                    )}
                    <p className="mt-[3px] text-[13px] text-[#AAAAAA]">
                      {doc.status === 'COMPLETED'
                        ? `Indexed on ${formatDate(doc.uploadedAt)}`
                        : `Uploaded ${formatDate(doc.uploadedAt)}`}
                    </p>
                  </Link>
                  <div className="mt-4 flex items-center justify-between border-t border-[#F5F5F5] pt-[14px]">
                    <Link
                      href={`/vault/documents/${doc.id}`}
                      className={`text-[11px] font-semibold uppercase tracking-[.1em] ${
                        doc.status === 'FAILED' ? 'text-[#B91C1C]' : 'text-[#666666]'
                      }`}
                    >
                      {documentActionLabel(doc.status)}
                    </Link>
                    <div className="flex items-center gap-2">
                      <span className="hidden text-xs text-[#AAAAAA] sm:inline">
                        {edgarDocument ? doc.companyName || doc.ticker || 'SEC filing' : formatBytes(doc.fileSize)}
                      </span>
                      <button
                        onClick={() => handleDelete(doc.id)}
                        className="rounded-md p-1.5 text-[#EF4444] opacity-0 transition-opacity hover:bg-[#FEF2F2] group-hover:opacity-100"
                        aria-label={`Delete ${doc.fileName}`}
                      >
                        <Trash2 size={14} />
                      </button>
                      <ChevronRight
                        size={15}
                        className={doc.status === 'FAILED' ? 'text-[#EF4444]' : 'text-[#AAAAAA]'}
                      />
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </div>
    </>
  );
}
