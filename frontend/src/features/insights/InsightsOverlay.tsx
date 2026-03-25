'use client';

import React from 'react';
import { X, PieChart, Trash2 } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
import { Document } from '@/lib/types';
import { useDocumentInsights } from '@/hooks/useDocuments';
import Button from '@/components/ui/Button';
import { LoadingIndicator } from '@/components/common/LoadingIndicator';

interface InsightsOverlayProps {
  doc: Document;
  token: string;
  onDeleteDoc: (doc: Document) => void;
  deletingId?: string | null;
  onClose: () => void;
}

export const InsightsOverlay: React.FC<InsightsOverlayProps> = ({
  doc,
  token,
  onDeleteDoc,
  deletingId,
  onClose,
}) => {
  const { viewUrl } = useDocumentInsights(doc.id, token);
  const cleanedSummary =
    typeof doc.aiSummary === 'string' ? doc.aiSummary.trim() : '';
  const summaryText =
    cleanedSummary.length > 0
      ? cleanedSummary
      : 'No extractable text was found in this document. Upload a readable file to generate an AI summary.';
  const markdownSummary = summaryText.replace(/^\s*•\s+/gm, '- ');

  return (
    <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-2xl z-50 flex items-center justify-center p-8">
      <div className="bg-white w-full max-w-7xl h-full rounded-3xl shadow-3xl overflow-hidden flex flex-col animate-in slide-in-from-bottom-20 duration-500 border border-white/20">
        <header className="px-12 py-10 border-b flex justify-between items-center bg-slate-50/20 backdrop-blur-md">
          <div className="flex items-center gap-5">
            <div className="bg-indigo-600 p-3 text-white rounded-2xl shadow-xl shadow-indigo-100">
              <PieChart size={24} />
            </div>
            <div>
              <h2 className="font-black text-2xl tracking-tight text-slate-800">
                {doc.fileName}
              </h2>
              <p className="text-[10px] font-black text-slate-400 uppercase tracking-[0.3em]">
                Audited Entity Context (UUID: {doc.id})
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-4 bg-white border border-slate-100 rounded-full hover:bg-slate-50 shadow-sm transition-all"
          >
            <X size={24} />
          </button>
        </header>

        <div className="flex-1 flex overflow-hidden">
          <div className="w-120 border-r px-12 py-8 overflow-y-auto space-y-12 bg-white/50">
            <section>
              <p className="text-[12px] font-black text-indigo-600 uppercase tracking-[0.2em] mb-4">
                AI Summary
              </p>
              <div className="text-base leading-relaxed text-slate-600 italic border-l-8 border-indigo-100 px-4 bg-indigo-50/20 py-4 rounded-r-3xl font-medium shadow-sm">
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
                      <strong className="font-extrabold text-slate-700">
                        {children}
                      </strong>
                    ),
                    em: ({ children }) => (
                      <em className="italic text-slate-700">{children}</em>
                    ),
                    a: ({ children, href }) => (
                      <a
                        href={href}
                        target="_blank"
                        rel="noreferrer"
                        className="text-indigo-700 underline decoration-indigo-300 underline-offset-2"
                      >
                        {children}
                      </a>
                    ),
                    h1: ({ children }) => (
                      <h3 className="text-lg font-black text-slate-800 mt-4 mb-2 first:mt-0">
                        {children}
                      </h3>
                    ),
                    h2: ({ children }) => (
                      <h3 className="text-base font-black text-slate-800 mt-4 mb-2 first:mt-0">
                        {children}
                      </h3>
                    ),
                    h3: ({ children }) => (
                      <h4 className="text-sm font-black text-slate-800 mt-4 mb-2 first:mt-0">
                        {children}
                      </h4>
                    ),
                    code: ({ children }) => (
                      <code className="bg-slate-100 text-slate-800 rounded px-1.5 py-0.5 text-sm">
                        {children}
                      </code>
                    ),
                  }}
                >
                  {markdownSummary}
                </ReactMarkdown>
              </div>
            </section>

            <Button
              className="w-full py-5 -mt-4 text-red-700 border border-red-200 bg-red-600 hover:bg-red-700 shadow-sm"
              loading={deletingId === doc.id}
              disabled={deletingId === doc.id}
              onClick={() => onDeleteDoc(doc)}
            >
              {deletingId === doc.id
                ? 'Deleting Document...'
                : 'Delete Document'}{' '}
              <Trash2 size={18} />
            </Button>
          </div>

          <div className="flex-1 bg-slate-100 relative">
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
