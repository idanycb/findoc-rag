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
import ReactMarkdown from 'react-markdown';
import remarkBreaks from 'remark-breaks';
import remarkGfm from 'remark-gfm';
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

interface RagAssistantViewProps {
  token: string;
}

const createMessage = (role: MessageRole, content: string): ChatMessage => ({
  id: `${role}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  role,
  content,
  createdAt: Date.now(),
});

const getMarkdownClassName = (role: MessageRole) =>
  [
    'text-sm font-medium leading-relaxed break-words',
    '[&_p]:mb-3 [&_p:last-child]:mb-0',
    '[&_ul]:my-3 [&_ul]:list-disc [&_ul]:pl-5',
    '[&_ol]:my-3 [&_ol]:list-decimal [&_ol]:pl-5',
    '[&_li]:my-1',
    '[&_h1]:mb-2 [&_h1]:text-base [&_h1]:font-black',
    '[&_h2]:mb-2 [&_h2]:text-sm [&_h2]:font-black',
    '[&_pre]:my-3 [&_pre]:overflow-x-auto [&_pre]:rounded-xl [&_pre]:p-3',
    '[&_code]:rounded [&_code]:px-1.5 [&_code]:py-0.5',
    '[&_a]:underline [&_a]:underline-offset-4',
    role === 'user'
      ? '[&_a]:text-white [&_pre]:bg-black/20 [&_code]:bg-black/20'
      : '[&_a]:text-neutral-900 [&_pre]:bg-neutral-100 [&_code]:bg-neutral-100',
  ].join(' ');

export const RagAssistantView = ({ token }: RagAssistantViewProps) => {
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
        throw new Error(data.error || 'No answer returned from assistant');
      }

      setMessages((prev) => [...prev, createMessage('assistant', answer)]);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Failed to reach assistant';
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
    <Card className="flex h-full min-h-0 flex-col overflow-hidden border-none bg-white/95 p-0 shadow-2xl backdrop-blur-sm">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-neutral-200 px-4 py-4 sm:px-6 sm:py-5">
        <div className="flex items-center gap-3 text-neutral-800">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-neutral-200 text-neutral-900">
            <Sparkles size={18} />
          </div>
          <div>
            <h3 className="text-base font-black tracking-tight">
              Chat With RAG Assistant
            </h3>
            <p className="text-[10px] font-bold uppercase tracking-widest text-neutral-500 sm:text-xs">
              Grounded answers from your uploaded knowledge base
            </p>
          </div>
        </div>

        <Button
          variant="ghost"
          className="px-3 py-2 text-neutral-600"
          onClick={() => {
            setMessages([]);
            setError(null);
          }}
          disabled={!hasMessages || isAsking}
        >
          <Trash2 size={16} /> Clear
        </Button>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto bg-linear-to-b from-neutral-100/60 to-white px-4 py-5 sm:px-6 sm:py-6">
        {!hasMessages && (
          <div className="flex h-full items-center justify-center">
            <div className="max-w-xl text-center">
              <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-neutral-200 text-neutral-800">
                <Bot size={24} />
              </div>
              <h4 className="mb-3 text-xl font-black text-neutral-900">
                Ask Anything About Your Documents
              </h4>
              <p className="text-sm font-medium leading-relaxed text-neutral-600">
                Try: &quot;What is the document about?&quot; or &quot;What are
                the key insights?&quot;
              </p>
            </div>
          </div>
        )}

        <div className="space-y-5">
          {messages.map((message) => (
            <div
              key={message.id}
              className={`max-w-[90%] rounded-3xl px-4 py-4 shadow-sm sm:max-w-[85%] sm:px-5 ${
                message.role === 'user'
                  ? 'ml-auto bg-neutral-900 text-white'
                  : 'mr-auto border border-neutral-200 bg-white text-neutral-800'
              }`}
            >
              <p className="mb-2 text-[10px] font-black uppercase tracking-[0.16em] opacity-70">
                {message.role === 'user' ? 'You' : 'Assistant'}
              </p>
              <div className={getMarkdownClassName(message.role)}>
                <ReactMarkdown remarkPlugins={[remarkGfm, remarkBreaks]}>
                  {message.content}
                </ReactMarkdown>
              </div>
            </div>
          ))}

          {isAsking && (
            <div className="mr-auto max-w-[90%] rounded-3xl border border-neutral-200 bg-white px-4 py-4 text-neutral-800 shadow-sm sm:max-w-[85%] sm:px-5">
              <p className="mb-2 text-[10px] font-black uppercase tracking-[0.16em] opacity-70">
                Assistant
              </p>
              <div className="flex items-center gap-2">
                <span className="h-2 w-2 animate-pulse rounded-full bg-neutral-400" />
                <span className="h-2 w-2 animate-pulse rounded-full bg-neutral-400 [animation-delay:120ms]" />
                <span className="h-2 w-2 animate-pulse rounded-full bg-neutral-400 [animation-delay:240ms]" />
              </div>
            </div>
          )}

          <div ref={endOfChatRef} />
        </div>
      </div>

      <form
        onSubmit={submitQuestion}
        className="border-t border-neutral-200 bg-white p-4 sm:p-6"
      >
        <div className="relative rounded-3xl border border-neutral-300 bg-neutral-100 px-4 py-3 transition-all focus-within:ring-2 ring-neutral-400 sm:px-5 sm:py-4">
          <textarea
            ref={composerRef}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            rows={1}
            className="w-full min-h-4 max-h-24 resize-none bg-transparent text-sm font-medium text-neutral-800 outline-none placeholder:text-neutral-500 overflow-y-hidden transition-[height] sm:pr-32"
            placeholder={
              'Ask about summaries, trends, key facts, or comparisons...\n\nPress Enter to send, Shift+Enter for newline.'
            }
            disabled={isAsking || isTokenUnavailable}
          />
          <div className="mt-3 flex sm:absolute sm:bottom-2 sm:right-2 sm:mt-0">
            <Button
              type="submit"
              className="w-full px-5 py-2.5 sm:w-auto"
              loading={isAsking}
              disabled={isAsking || !question.trim() || isTokenUnavailable}
            >
              <Send size={16} /> Ask
            </Button>
          </div>
        </div>

        {error && (
          <p className="mt-3 text-xs font-bold text-neutral-700" role="alert">
            {error}
          </p>
        )}
      </form>
    </Card>
  );
};
