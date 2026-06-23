'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import {
  ChevronLeft,
  Eye,
  RotateCcw,
  Trash2,
  Download,
  Check,
  MessageSquare,
} from 'lucide-react';
import { apiCall, ApiError } from '@/shared/lib/api';
import { fetchViewUrl } from '@/shared/lib/documents';
import { formatBytes, formatDate } from '@/shared/lib/auth';
import type { DocumentDetailResponse } from '@/shared/types';
import { StatusBadge } from './StatusBadge';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';
import { useRequireRole } from '@/shared/hooks/useRequireRole';

const POLL_INTERVAL = 4000;
const DOCUMENT_DETAIL_ROLES = ['ADMIN', 'MEMBER'] as const;

export function DocumentDetailClient({ id }: { id: string }) {
  const { token } = useAuth();
  const { isCheckingAccess } = useRequireRole([...DOCUMENT_DETAIL_ROLES]);
  const router = useRouter();
  const [doc, setDoc] = useState<DocumentDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [analyzing, setAnalyzing] = useState(false);
  const [actionError, setActionError] = useState('');

  const fetchDoc = useCallback(async () => {
    if (!token) return;
    try {
      const data = await apiCall<DocumentDetailResponse>(`/documents/${id}`, undefined, token);
      setDoc(data);
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [id, token]);

  useEffect(() => {
    fetchDoc();
  }, [fetchDoc]);

  // Poll while processing
  useEffect(() => {
    if (!doc || (doc.status !== 'PROCESSING' && doc.status !== 'PENDING')) return;
    const timer = setInterval(fetchDoc, POLL_INTERVAL);
    return () => clearInterval(timer);
  }, [doc, fetchDoc]);

  const handleAnalyze = async () => {
    setActionError('');
    setAnalyzing(true);
    try {
      await apiCall(`/documents/${id}/analyze`, { method: 'POST' }, token);
      setDoc((current) => current ? { ...current, status: 'PENDING' } : current);
      await fetchDoc();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Analysis request failed.');
    } finally {
      setAnalyzing(false);
    }
  };

  const handleView = async () => {
    setActionError('');
    try {
      const viewUrl = await fetchViewUrl(id, token);
      window.open(viewUrl, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Could not get view URL.');
    }
  };

  const handleDelete = async () => {
    if (!confirm('Delete this document? This cannot be undone.')) return;
    try {
      await apiCall(`/documents/${id}`, { method: 'DELETE' }, token);
      router.replace('/vault');
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Delete failed.');
    }
  };

  if (isCheckingAccess || loading) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#888888]">Loading…</div>
    );
  }

  if (error || !doc) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-4 p-8">
        <p className="text-[#B91C1C] text-[14px]">{error || 'Document not found.'}</p>
        <Link href="/vault" className="text-[13px] text-[#111111] font-semibold hover:underline">
          ← Back to vault
        </Link>
      </div>
    );
  }

  const canAnalyze = doc.status === 'PENDING' || doc.status === 'FAILED';
  const canOpenChat = doc.status === 'COMPLETED';
  const analysisQueued = doc.status === 'PENDING' || doc.status === 'PROCESSING';
  const analyzeLabel = analyzing
    ? 'Requesting…'
    : doc.status === 'FAILED'
      ? 'Retry analysis'
      : doc.status === 'COMPLETED'
        ? 'Analysis complete'
        : 'Analyze';

  return (
    <>
      {/* Header */}
      <div className="flex items-center gap-4 bg-white px-5 py-4 border-b border-[#EBEBEB] md:px-7">
        <Link
          href="/vault"
          className="w-[34px] h-[34px] rounded-lg border border-[#E5E5E5] bg-white flex items-center justify-center flex-none hover:bg-[#F5F5F5] transition-colors"
        >
          <ChevronLeft size={17} className="text-[#666666]" />
        </Link>
        <div className="flex-1 min-w-0">
          <div className="text-[11px] text-[#AAAAAA]">Workspace / Document</div>
          <h1 className="text-lg font-bold text-[#111111] truncate">{doc.fileName}</h1>
        </div>
        <div className="hidden sm:flex items-center gap-[10px]">
          <button
            onClick={handleView}
            className="flex items-center gap-[7px] border border-[#E5E5E5] bg-white text-[#333333] text-[13.5px] font-medium px-[14px] h-9 rounded-lg hover:bg-[#F5F5F5] transition-colors"
          >
            <Eye size={15} />
            View file
          </button>
          <button
            onClick={handleAnalyze}
            disabled={analyzing || !canAnalyze}
            className="flex items-center gap-[7px] border border-[#E5E5E5] bg-white text-[#333333] text-[13.5px] font-medium px-[14px] h-9 rounded-lg hover:bg-[#F5F5F5] transition-colors disabled:opacity-60"
          >
            <RotateCcw size={15} />
            {analyzeLabel}
          </button>
          <button
            onClick={handleDelete}
            className="flex items-center gap-[7px] border border-[#FECACA] bg-[#FEF2F2] text-[#B91C1C] text-[13.5px] font-medium px-[14px] h-9 rounded-lg hover:bg-[#FEE2E2] transition-colors"
          >
            <Trash2 size={15} />
            Delete
          </button>
        </div>
      </div>

      {actionError && (
        <div className="mx-5 md:mx-7 mt-4 text-[13px] text-[#B91C1C] bg-[#FEF2F2] border border-[#FECACA] rounded-[9px] px-4 py-3">
          {actionError}
        </div>
      )}

      {/* Body */}
      <div className="flex flex-col lg:flex-row gap-5 p-5 md:p-7">
        {/* Left: summary */}
        <div className="flex-1 min-w-0 flex flex-col gap-4">
          <div className="rounded-[14px] bg-white px-6 py-5 shadow-[0_1px_4px_rgba(0,0,0,.06)] flex flex-col gap-3 sm:flex-row sm:items-center">
            <StatusBadge status={doc.status} />
            <div className="flex items-center gap-[6px] text-[13.5px] text-[#666666]">
              {doc.status === 'COMPLETED' && <Check size={14} className="text-[#22C55E]" strokeWidth={2.5} />}
              {doc.status === 'COMPLETED'
                ? `Analysis finished${doc.lastAnalyzedAt ? ` · last analyzed ${formatDate(doc.lastAnalyzedAt)}` : ''}`
                : analysisQueued
                  ? 'Analysis in progress · polling for updates'
                  : doc.status === 'FAILED'
                    ? 'Analysis failed · re-run analysis to retry'
                    : 'Queued for analysis'}
            </div>
          </div>

          <div className="rounded-[14px] bg-white p-6 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="mb-[18px] text-[11px] font-bold uppercase tracking-[.12em] text-[#AAAAAA]">
              File Information
            </div>
            <div className="grid grid-cols-1 gap-[18px] sm:grid-cols-2">
              {[
                ['File name', doc.fileName],
                ['Content type', doc.contentType],
                ['File size', formatBytes(doc.fileSize)],
                ['Uploaded at', formatDate(doc.uploadedAt)],
                ['Last analyzed at', doc.lastAnalyzedAt ? formatDate(doc.lastAnalyzedAt) : 'Never'],
                ['Document ID', doc.id],
              ].map(([label, value]) => (
                <div key={label}>
                  <div className="mb-1 text-xs text-[#AAAAAA]">{label}</div>
                  <div
                    className={`text-sm font-semibold ${
                      label === 'Document ID'
                        ? 'font-mono text-xs break-all text-[#888888]'
                        : label === 'Last analyzed at' && doc.lastAnalyzedAt
                          ? 'text-[#22C55E]'
                          : 'text-[#111111]'
                    }`}
                  >
                    {value}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="rounded-[14px] bg-white p-6 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="flex items-start gap-[14px]">
              <div className="w-10 h-10 rounded-[11px] bg-[#F5F5F5] flex items-center justify-center flex-none">
                <MessageSquare size={20} className="text-[#666666]" />
              </div>
              <div>
                <h2 className="text-[15px] font-bold text-[#111111]">
                  Ask questions about this document
                </h2>
                <p className="mt-[5px] text-[13.5px] leading-[1.6] text-[#888888]">
                  Document summaries are generated on demand through RAG Chat. This document is
                  indexed and ready when analysis is complete.
                </p>
                {canOpenChat ? (
                  <Link
                    href="/chat"
                    className="mt-[14px] inline-flex items-center gap-[7px] rounded-lg bg-[#111111] px-4 py-2 text-[13px] font-semibold text-white hover:bg-[#333333]"
                  >
                    <MessageSquare size={14} />
                    Open RAG Chat
                  </Link>
                ) : (
                  <button
                    type="button"
                    disabled
                    className="mt-[14px] inline-flex items-center gap-[7px] rounded-lg bg-[#111111] px-4 py-2 text-[13px] font-semibold text-white opacity-45"
                  >
                    <MessageSquare size={14} />
                    Chat available after analysis
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Right: metadata */}
        <div className="w-full lg:w-[268px] flex-none flex flex-col gap-[14px]">
          <div className="rounded-[14px] bg-white p-[18px] shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="mb-[14px] text-[11px] font-bold uppercase tracking-[.12em] text-[#AAAAAA]">
              Analysis
            </div>
            <div className="flex flex-col gap-[10px]">
              <div className="flex items-center gap-[9px]">
                <div
                  className={`h-[7px] w-[7px] rounded-full flex-none ${
                    doc.status === 'COMPLETED'
                      ? 'bg-[#22C55E]'
                      : doc.status === 'FAILED'
                        ? 'bg-[#EF4444]'
                        : 'bg-[#AAAAAA]'
                  }`}
                />
                <div className="text-[13.5px] text-[#111111]">
                  {doc.status === 'COMPLETED'
                    ? 'Indexed and ready for questions'
                    : doc.status === 'FAILED'
                      ? 'Indexing failed'
                      : 'Indexing is not complete yet'}
                </div>
              </div>
              <p className="pl-4 text-xs leading-[1.55] text-[#AAAAAA]">
                Analysis can be requested while the document is pending or failed. Completed and
                actively processing documents are left unchanged.
              </p>
            </div>
          </div>

          {/* Actions */}
          <div className="rounded-[14px] bg-white p-1.5 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="flex flex-col">
              <button
                onClick={handleView}
                className="flex items-center gap-[10px] w-full rounded-[9px] px-3 py-[11px] text-sm font-medium text-[#333333] hover:bg-[#F5F5F5]"
              >
                <Download size={15} className="text-[#666666]" />
                Download raw file
              </button>
              <button
                onClick={handleAnalyze}
                disabled={analyzing || !canAnalyze}
                className="flex items-center gap-[10px] w-full rounded-[9px] px-3 py-[11px] text-sm font-medium text-[#333333] hover:bg-[#F5F5F5] disabled:opacity-60"
              >
                <RotateCcw size={15} className="text-[#666666]" />
                Re-run analysis
              </button>
              <button
                onClick={handleDelete}
                className="flex items-center gap-[10px] w-full rounded-[9px] px-3 py-[11px] text-sm font-medium text-[#B91C1C] hover:bg-[#FEF2F2]"
              >
                <Trash2 size={15} />
                Delete document
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
