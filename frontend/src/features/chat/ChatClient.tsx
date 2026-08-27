'use client';

import { useState, useRef, useEffect, useTransition } from 'react';
import { MessageSquare, Send } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import { apiCall, ApiError } from '@/shared/lib/api';
import { getUserInitials } from '@/shared/lib/auth';
import { useAuth } from '@/context/AuthContext';
import { useDocuments } from '@/shared/hooks/useDocuments';
import { useRequireRole } from '@/shared/hooks/useRequireRole';

interface Message {
  role: 'user' | 'assistant';
  content: string;
}

const SUGGESTIONS = [
  'Summarize the risk factors',
  'What changed in MD&A?',
  'Highlight liquidity concerns',
  'Compare this filing to prior reports',
];
const MAX_QUESTION_LENGTH = 1000;
const CHAT_ROLES = ['ADMIN', 'MEMBER'] as const;

export function ChatClient() {
  const { token, claims } = useAuth();
  const { isCheckingAccess } = useRequireRole([...CHAT_ROLES]);
  const { documents, error: documentsError } = useDocuments({ enabled: !isCheckingAccess });
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const initials = claims ? getUserInitials(claims.sub) : 'U';
  const indexedCount = documentsError
    ? null
    : documents.filter((doc) => doc.status === 'COMPLETED').length;

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const sendMessage = (question: string) => {
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion || normalizedQuestion.length > MAX_QUESTION_LENGTH || isPending) return;
    setError('');
    const userMsg: Message = { role: 'user', content: normalizedQuestion };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');

    startTransition(async () => {
      try {
        const data = await apiCall<{ answer: string }>(
          '/chat',
          { method: 'POST', body: JSON.stringify({ question: normalizedQuestion }) },
          token
        );
        setMessages((prev) => [...prev, { role: 'assistant', content: data.answer }]);
      } catch (err) {
        const msg = err instanceof ApiError ? err.message : 'Something went wrong.';
        setError(msg);
        setMessages((prev) => [...prev, { role: 'assistant', content: `Error: ${msg}` }]);
      }
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    sendMessage(input);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage(input);
    }
  };

  const showEmpty = messages.length === 0;

  if (isCheckingAccess) {
    return <div className="flex-1 flex items-center justify-center text-[#888888]">Loading…</div>;
  }

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* Header */}
      <div className="flex items-center justify-between bg-white px-5 py-4 border-b border-[#EBEBEB] md:px-7">
        <div>
          <h1 className="text-[22px] font-bold tracking-[-.01em] text-[#111111]">RAG Chat</h1>
          <p className="mt-0.5 text-[11px] uppercase tracking-[.12em] text-[#AAAAAA]">
            Grounded in your team vault
          </p>
        </div>
        <div className="hidden items-center gap-2 rounded-lg border border-[#E8E8E8] bg-[#F5F5F5] px-3.25 py-1.75 sm:flex">
          <span className="h-1.75 w-1.75 rounded-full bg-[#22C55E]" />
          <span className="text-[13px] font-medium text-[#555555]">
            {indexedCount === null
              ? 'Vault indexed'
              : `${indexedCount} document${indexedCount === 1 ? '' : 's'} indexed`}
          </span>
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-auto px-5 py-4 md:px-7 md:py-7 flex flex-col gap-5">
        {showEmpty && (
          <div className="flex gap-3.25 max-w-170">
            <div className="w-8 h-8 rounded-[9px] bg-[#111111] flex items-center justify-center flex-none">
              <MessageSquare size={15} className="text-white" />
            </div>
            <div>
              <div className="bg-white rounded-[4px_14px_14px_14px] px-4.5 py-3.5 shadow-[0_1px_3px_rgba(0,0,0,.06)]">
                <p className="text-[14.5px] text-[#333333] leading-[1.65]">
                  Hi {claims?.sub ?? 'there'} — I can answer questions grounded in your
                  team&apos;s indexed documents
                  {indexedCount !== null ? (
                    <>
                      . I have{' '}
                      <strong className="text-[#111111]">
                        {indexedCount} document{indexedCount === 1 ? '' : 's'}
                      </strong>{' '}
                      available
                    </>
                  ) : null}
                  . What would you like to know?
                </p>
              </div>
              <span className="text-[11px] text-[#BBBBBB] mt-1.25 ml-1 block">
                just now
              </span>
            </div>
          </div>
        )}

        {messages.map((msg, i) =>
          msg.role === 'user' ? (
            <div
              key={i}
              className="flex gap-3.25 max-w-170 self-end flex-row-reverse"
            >
              <div className="w-8 h-8 rounded-full bg-[#E5E5E5] flex items-center justify-center flex-none text-[11px] font-bold text-[#555555]">
                {initials}
              </div>
              <div className="bg-[#111111] rounded-[14px_4px_14px_14px] px-4.25 py-3">
                <p className="text-[14.5px] text-white leading-[1.6]">{msg.content}</p>
              </div>
            </div>
          ) : (
            <div key={i} className="flex gap-3.25 max-w-170">
              <div className="w-8 h-8 rounded-[9px] bg-[#111111] flex items-center justify-center flex-none">
                <MessageSquare size={15} className="text-white" />
              </div>
              <div>
                <div className="bg-white rounded-[4px_14px_14px_14px] px-4.5 py-3.5 shadow-[0_1px_3px_rgba(0,0,0,.06)]">
                  <div className="prose prose-sm max-w-none text-[#333333] leading-[1.7] [&_strong]:text-[#111111] [&_code]:text-[#111111]">
                    <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>
                      {msg.content}
                    </ReactMarkdown>
                  </div>
                </div>
                <span className="text-[11px] text-[#BBBBBB] mt-1.25 ml-1 block">
                  just now
                </span>
              </div>
            </div>
          )
        )}

        {isPending && (
          <div className="flex gap-3.25 max-w-170">
            <div className="w-8 h-8 rounded-[9px] bg-[#111111] flex items-center justify-center flex-none">
              <MessageSquare size={15} className="text-white" />
            </div>
            <div className="bg-white rounded-[4px_14px_14px_14px] px-4.5 py-3.5 shadow-[0_1px_3px_rgba(0,0,0,.06)]">
              <span className="text-[14px] text-[#888888] animate-pulse">Thinking…</span>
            </div>
          </div>
        )}

        <div ref={bottomRef} />
      </div>

      {/* Suggestions + input */}
      <div className="px-5 pb-4 md:px-7 md:pb-6">
        {showEmpty && (
          <div className="flex gap-2.25 mb-3 flex-wrap">
            {SUGGESTIONS.map((s) => (
              <button
                key={s}
                onClick={() => sendMessage(s)}
                className="text-[12.5px] text-[#666666] border border-[#E5E5E5] bg-white rounded-full px-3.25 py-1.5 hover:border-[#BBBBBB] hover:text-[#111111] transition-colors"
              >
                {s}
              </button>
            ))}
          </div>
        )}

        {error && (
          <div className="mb-3 text-[13px] text-[#B91C1C] bg-[#FEF2F2] border border-[#FECACA] rounded-[9px] px-4 py-3">
            {error}
          </div>
        )}

        <form
          onSubmit={handleSubmit}
          className="flex items-center gap-2.5 bg-white border border-[#E5E5E5] rounded-[14px] p-2 pl-4.5 shadow-[0_1px_3px_rgba(0,0,0,.05)]"
        >
          <textarea
            ref={textareaRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask a question about your documents…"
            maxLength={MAX_QUESTION_LENGTH}
            rows={1}
            className="flex-1 bg-transparent text-[14.5px] text-[#111111] placeholder:text-[#BBBBBB] outline-hidden resize-none"
          />
          <button
            type="submit"
            disabled={isPending || !input.trim() || input.trim().length > MAX_QUESTION_LENGTH}
            className="w-9.5 h-9.5 rounded-[10px] bg-[#111111] flex items-center justify-center hover:bg-[#333333] transition-colors disabled:opacity-40 flex-none"
          >
            <Send size={16} className="text-white" />
          </button>
        </form>

        <p className="text-center text-[11.5px] text-[#CCCCCC] mt-2.5">
          Answers are grounded in your team&apos;s indexed documents. Always verify important
          figures.
        </p>
      </div>
    </div>
  );
}
