'use client';

import React from 'react';

export const LoadingIndicator: React.FC = () => (
  <div className="flex h-96 flex-col items-center justify-center gap-6 text-[10px] font-bold uppercase italic tracking-[0.4em] text-neutral-500">
    <div className="h-16 w-16 animate-spin rounded-full border-8 border-neutral-200 border-t-neutral-900 shadow-xl"></div>
    Syncing Retrieval Index...
  </div>
);
