'use client';

import React from 'react';

export const LoadingIndicator: React.FC = () => (
  <div className="h-96 flex flex-col items-center justify-center text-slate-300 font-bold gap-6 italic uppercase tracking-[0.4em] text-[10px]">
    <div className="w-16 h-16 border-8 border-slate-100 border-t-indigo-600 rounded-full animate-spin shadow-xl"></div>
    Syncing S3 Encryption Nodes...
  </div>
);
