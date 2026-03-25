'use client';

import { useAuth } from '@/context/AuthContext';
import { AIAnalystView } from '@/features/ai-analysis/AIAnalystView';

export default function ChatPage() {
  const { token } = useAuth();

  return (
    <>
      <section className="flex-1 min-h-0 p-6 md:p-12 bg-[#F8FAFC]/50 overflow-hidden">
        <div className="max-w-7xl mx-auto h-full min-h-0">
          <div className="animate-in fade-in slide-in-from-bottom-2 duration-500 h-full min-h-0">
            <AIAnalystView token={token} />
          </div>
        </div>
      </section>
    </>
  );
}
