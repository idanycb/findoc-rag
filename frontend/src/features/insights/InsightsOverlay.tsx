'use client';

import React, { useEffect, useRef, useState } from 'react';
import { X, PieChart, RefreshCw, Trash2 } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
import { Document } from '@/lib/types';
import { useDocumentInsights } from '@/hooks/useDocuments';
import Button from '@/components/ui/Button';
import { LoadingIndicator } from '@/components/common/LoadingIndicator';
import { apiCall } from '@/lib/api';

interface InsightsOverlayProps {
  doc: Document;
  token: string;
  onDeleteDoc: (doc: Document) => void;
  onDocUpdated?: (doc: Document) => void;
  deletingId?: string | null;
  onClose: () => void;
}

export const InsightsOverlay: React.FC<InsightsOverlayProps> = ({
  doc,
  token,
  onDeleteDoc,
  onDocUpdated,
  deletingId,
  onClose,
}) => {
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const isMountedRef = useRef(true);
  const { viewUrl } = useDocumentInsights(doc.id, token);
  const cleanedSummary =
    typeof doc.aiSummary === 'string' ? doc.aiSummary.trim() : '';
  const summaryText =
    cleanedSummary.length > 0
      ? cleanedSummary
      : 'No extractable text was found in this document. Upload a readable file to generate an AI summary.';
  const markdownSummary = summaryText.replace(/^\s*•\s+/gm, '- ');

  useEffect(() => {
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const delay = (ms: number) =>
    new Promise((resolve) => {
      setTimeout(resolve, ms);
    });

  const handleReanalyzeDocument = async () => {
    if (isAnalyzing || deletingId === doc.id) {
      return;
    }

    try {
      if (isMountedRef.current) {
        setIsAnalyzing(true);
      }

      const analyzeResponse = (await apiCall(
        `/documents/${doc.id}/analyze`,
        { method: 'POST' },
        token
      )) as Document | null;

      if (analyzeResponse) {
        onDocUpdated?.(analyzeResponse);
      }

      let currentStatus = analyzeResponse?.status;

      while (isMountedRef.current && currentStatus === 'PROCESSING') {
        await delay(3000);

        const latestDocument = (await apiCall(
          `/documents/${doc.id}`,
          undefined,
          token
        )) as Document | null;

        if (!latestDocument) {
          break;
        }

        onDocUpdated?.(latestDocument);
        currentStatus = latestDocument.status;

        if (currentStatus === 'COMPLETED' || currentStatus === 'FAILED') {
          break;
        }
      }
    } catch (err) {
      console.error(`Failed to re-analyze document ${doc.id}:`, err);
      alert(
        err instanceof Error ? err.message : 'Failed to re-analyze document'
      );
    } finally {
      if (isMountedRef.current) {
        setIsAnalyzing(false);
      }
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-3 backdrop-blur-2xl sm:p-6">
      <div className="flex h-full max-h-[95vh] w-full max-w-7xl flex-col overflow-hidden rounded-3xl border border-white/20 bg-white shadow-3xl animate-in slide-in-from-bottom-20 duration-500">
        <header className="flex items-center justify-between border-b border-neutral-200 bg-neutral-100/60 px-4 py-4 backdrop-blur-md sm:px-8 sm:py-6">
          <div className="flex items-center gap-5">
            <div className="rounded-2xl bg-neutral-900 p-3 text-white shadow-xl shadow-neutral-300">
              <PieChart size={20} />
            </div>
            <div>
              <h2 className="text-lg font-black tracking-tight text-neutral-900 sm:text-2xl">
                {doc.fileName}
              </h2>
              <p className="text-[10px] font-black uppercase tracking-[0.2em] text-neutral-500 sm:tracking-[0.3em]">
                Document Context (ID: {doc.id})
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-full border border-neutral-300 bg-white p-3 shadow-sm transition-all hover:bg-neutral-100"
          >
            <X size={20} />
          </button>
        </header>

        <div className="flex flex-1 flex-col overflow-hidden lg:flex-row">
          <div className="w-full max-h-[46%] space-y-8 overflow-y-auto border-b border-neutral-200 bg-white/50 px-4 py-5 sm:px-8 sm:py-7 lg:w-120 lg:max-h-none lg:border-b-0 lg:border-r">
            <section>
              <p className="mb-4 text-[12px] font-black uppercase tracking-[0.2em] text-neutral-700">
                AI Summary
              </p>
              <div className="rounded-r-3xl border-l-8 border-neutral-300 bg-neutral-100 px-4 py-4 text-base font-medium italic leading-relaxed text-neutral-700 shadow-sm">
                <ReactMarkdown
                  remarkPlugins={[remarkGfm, remarkBreaks]}
                  components={{
                    p: ({ children }) => (
                      <p className="mb-3 last:mb-0">{children}</p>
                    ),
                    ul: ({ children }) => (
                      <ul className="list-disc space-y-2 pl-6 mb-3 last:mb-0">
                        {children}
                      </ul>
                    ),
                    ol: ({ children }) => (
                      <ol className="list-decimal space-y-2 pl-6 mb-3 last:mb-0">
                        {children}
                      </ol>
                    ),
                    li: ({ children }) => <li>{children}</li>,
                    strong: ({ children }) => (
                      <strong className="font-extrabold text-neutral-900">
                        {children}
                      </strong>
                    ),
                    em: ({ children }) => (
                      <em className="italic text-neutral-800">{children}</em>
                    ),
                    a: ({ children, href }) => (
                      <a
                        href={href}
                        target="_blank"
                        rel="noreferrer"
                        className="text-neutral-900 underline decoration-neutral-500 underline-offset-2"
                      >
                        {children}
                      </a>
                    ),
                    h1: ({ children }) => (
                      <h3 className="text-lg font-black text-neutral-900 mt-4 mb-2 first:mt-0">
                        {children}
                      </h3>
                    ),
                    h2: ({ children }) => (
                      <h3 className="text-base font-black text-neutral-900 mt-4 mb-2 first:mt-0">
                        {children}
                      </h3>
                    ),
                    h3: ({ children }) => (
                      <h4 className="text-sm font-black text-neutral-900 mt-4 mb-2 first:mt-0">
                        {children}
                      </h4>
                    ),
                    code: ({ children }) => (
                      <code className="rounded bg-neutral-200 px-1.5 py-0.5 text-sm text-neutral-900">
                        {children}
                      </code>
                    ),
                  }}
                >
                  {markdownSummary}
                </ReactMarkdown>
              </div>
            </section>

            <div className="space-y-3">
              <Button
                className="w-full py-4"
                loading={deletingId === doc.id}
                disabled={deletingId === doc.id || isAnalyzing}
                onClick={() => onDeleteDoc(doc)}
              >
                {deletingId === doc.id
                  ? 'Deleting Document...'
                  : 'Delete Document'}{' '}
                <Trash2 size={18} />
              </Button>

              <Button
                variant="secondary"
                className="w-full py-4"
                disabled={isAnalyzing || deletingId === doc.id}
                onClick={handleReanalyzeDocument}
              >
                {isAnalyzing ? (
                  <>
                    <RefreshCw size={18} className="animate-spin" />
                    Analyzing Document...
                  </>
                ) : (
                  <>
                    <RefreshCw size={18} />
                    Re-analyze Document
                  </>
                )}
              </Button>
            </div>
          </div>

          <div className="relative min-h-[40vh] flex-1 bg-neutral-100 lg:min-h-0">
            {viewUrl ? (
              <iframe
                src={viewUrl}
                className="w-full h-full border-none shadow-inner"
              />
            ) : (
              <LoadingIndicator />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
