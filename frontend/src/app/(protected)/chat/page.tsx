'use client';

import { useAuth } from '@/context/AuthContext';
import { RagAssistantView } from '@/features/ai-analysis/AIAnalystView';

export default function ChatPage() {
  const { token } = useAuth();

  return (
    <>
      <section className="flex-1 min-h-0 overflow-hidden bg-neutral-100/70 p-3 sm:p-6 lg:p-10">
        <div className="max-w-7xl mx-auto h-full min-h-0">
          <div className="animate-in fade-in slide-in-from-bottom-2 duration-500 h-full min-h-0">
            <RagAssistantView token={token} />
          </div>
        </div>
      </section>
    </>
  );
}
