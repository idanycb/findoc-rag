'use client';

import {
  FormEvent,
  KeyboardEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Bot, Send, Sparkles, Trash2 } from 'lucide-react';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import { apiCall } from '@/lib/api';

type MessageRole = 'user' | 'assistant';

type ChatMessage = {
  id: string;
  role: MessageRole;
  content: string;
  createdAt: number;
};

type ChatResponse = {
  answer?: string;
  error?: string;
};

interface AIAnalystViewProps {
  token: string;
}

const createMessage = (role: MessageRole, content: string): ChatMessage => ({
  id: `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  role,
  content,
  createdAt: Date.now(),
});

export const AIAnalystView = ({ token }: AIAnalystViewProps) => {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [question, setQuestion] = useState('');
  const [isAsking, setIsAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isMounted, setIsMounted] = useState(false);
  const endOfChatRef = useRef<HTMLDivElement | null>(null);
  const composerRef = useRef<HTMLTextAreaElement | null>(null);

  const syncComposerHeight = () => {
    const textarea = composerRef.current;

    if (!textarea) {
      return;
    }

    const maxHeight = 96;
    textarea.style.height = 'auto';
    textarea.style.height = `${Math.min(textarea.scrollHeight, maxHeight)}px`;
    textarea.style.overflowY =
      textarea.scrollHeight > maxHeight ? 'auto' : 'hidden';
  };

  useEffect(() => {
    endOfChatRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, isAsking]);

  useEffect(() => {
    syncComposerHeight();
  }, [question]);

  useEffect(() => {
    setIsMounted(true);
  }, []);

  const hasMessages = useMemo(() => messages.length > 0, [messages.length]);
  const isTokenUnavailable = isMounted && !token;

  const submitQuestion = async (event?: FormEvent) => {
    event?.preventDefault();

    const trimmedQuestion = question.trim();

    if (!trimmedQuestion || isAsking || !token) {
      return;
    }

    const userMessage = createMessage('user', trimmedQuestion);
    setMessages((prev) => [...prev, userMessage]);
    setQuestion('');
    setError(null);
    setIsAsking(true);

    try {
      const response = await apiCall(
        '/chat',
        {
          method: 'POST',
          body: JSON.stringify({ question: trimmedQuestion }),
        },
        token
      );

      const data = response as ChatResponse;
      const answer = data.answer?.trim();

      if (!answer) {
        throw new Error(data.error || 'No answer returned from AI analyst');
      }

      setMessages((prev) => [...prev, createMessage('assistant', answer)]);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to reach AI analyst';
      setError(message);
      setMessages((prev) => [
        ...prev,
        createMessage(
          'assistant',
          'I could not process that request right now. Please try again in a moment.'
        ),
      ]);
    } finally {
      setIsAsking(false);
    }
  };

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void submitQuestion();
    }
  };

  return (
    <Card className="p-0 h-full min-h-0 overflow-hidden border-none shadow-2xl bg-white/90 backdrop-blur-sm flex flex-col">
      <div className="border-b border-slate-100 px-8 py-6 flex flex-wrap gap-4 items-center justify-between">
        <div className="flex items-center gap-3 text-slate-700">
          <div className="w-10 h-10 rounded-2xl bg-indigo-50 text-indigo-600 flex items-center justify-center">
            <Sparkles size={18} />
          </div>
          <div>
            <h3 className="text-base font-black tracking-tight">
              Chat With AI Analyst
            </h3>
            <p className="text-xs font-bold text-slate-400 uppercase tracking-widest">
              Financial Q&A grounded in your uploaded documents
            </p>
          </div>
        </div>

        <Button
          variant="ghost"
          className="px-4 py-2 text-slate-500"
          onClick={() => {
            setMessages([]);
            setError(null);
          }}
          disabled={!hasMessages || isAsking}
        >
          <Trash2 size={16} /> Clear
        </Button>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto px-8 py-7 bg-linear-to-b from-slate-50/60 to-white">
        {!hasMessages && (
          <div className="h-full flex items-center justify-center">
            <div className="max-w-xl text-center">
              <div className="mx-auto mb-4 w-14 h-14 rounded-2xl bg-indigo-100 text-indigo-600 flex items-center justify-center">
                <Bot size={24} />
              </div>
              <h4 className="text-xl font-black text-slate-800 mb-3">
                Ask Anything About Your Financial Docs
              </h4>
              <p className="text-sm text-slate-500 font-medium leading-relaxed">
                Try: &quot;Summarize the highest risk items across all uploaded
                reports&quot; or &quot;What changed between the latest and
                previous statements?&quot;
              </p>
            </div>
          </div>
        )}

        <div className="space-y-5">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`max-w-[85%] rounded-3xl px-5 py-4 shadow-sm ${
                message.role === 'user'
                  ? 'ml-auto bg-indigo-600 text-white'
                  : 'mr-auto bg-white border border-slate-100 text-slate-700'
              }`}
            >
              <p className="text-[10px] font-black uppercase tracking-[0.16em] opacity-70 mb-2">
                {message.role === 'user' ? 'You' : 'AI Analyst'}
              </p>
              <p className="text-sm leading-relaxed whitespace-pre-wrap wrap-break-word font-medium">
                {message.content}
              </p>
            </div>
          ))}

          {isAsking && (
            <div className="max-w-[85%] mr-auto bg-white border border-slate-100 text-slate-700 rounded-3xl px-5 py-4 shadow-sm">
              <p className="text-[10px] font-black uppercase tracking-[0.16em] opacity-70 mb-2">
                AI Analyst
              </p>
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-slate-300 animate-pulse" />
                <span className="w-2 h-2 rounded-full bg-slate-300 animate-pulse [animation-delay:120ms]" />
                <span className="w-2 h-2 rounded-full bg-slate-300 animate-pulse [animation-delay:240ms]" />
              </div>
            </div>
          )}

          <div ref={endOfChatRef} />
        </div>
      </div>

      <form
        onSubmit={submitQuestion}
        className="border-t border-slate-100 p-6 bg-white"
      >
        <div className="rounded-3xl border border-slate-200 bg-slate-50 py-4 px-6 focus-within:ring-2 ring-indigo-100 transition-all relative">
          <textarea
            ref={composerRef}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            rows={1}
            className="w-full min-h-4 max-h-24 resize-none bg-transparent outline-none text-sm text-slate-700 placeholder:text-slate-400 font-medium overflow-y-hidden transition-[height]"
            placeholder={
              'Ask about trends, anomalies, compliance concerns, or executive summaries...\n\nPress Enter to send, Shift+Enter for newline.'
            }
            disabled={isAsking || isTokenUnavailable}
          />
          <Button
            type="submit"
            className="px-6 py-3 absolute right-2 bottom-2"
            loading={isAsking}
            disabled={isAsking || !question.trim() || isTokenUnavailable}
          >
            <Send size={16} /> Ask Analyst
          </Button>
        </div>
        <div className="-mb-8 mt-4 flex items-center justify-between gap-4 px-4">
          <p className="text-[11px] font-semibold text-slate-400"></p>
        </div>

        {error && (
          <p className="text-xs font-bold text-rose-600 mt-3" role="alert">
            {error}
          </p>
        )}
      </form>
    </Card>
  );
};
