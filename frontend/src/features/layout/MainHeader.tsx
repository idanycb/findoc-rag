'use client';

import React from 'react';
import { Search, Upload } from 'lucide-react';
import Button from '@/components/ui/Button';

interface MainHeaderProps {
  title: string;
  subtitle?: string;
  onUploadClick?: () => void;
}

export const MainHeader: React.FC<MainHeaderProps> = ({
  title,
  subtitle = 'Status: Operational (v2.5.1)',
  onUploadClick,
}) => {
  return (
    <header className="h-28 px-12 border-b border-slate-50 flex items-center justify-between bg-white/60 backdrop-blur-xl sticky top-0 z-20">
      <div>
        <h2 className="text-3xl font-black text-slate-800 tracking-tight capitalize">
          {title}
        </h2>
        <p className="text-[10px] font-black text-slate-400 uppercase tracking-widest mt-1">
          {subtitle}
        </p>
      </div>
      <div className="flex items-center gap-6">
        <div className="hidden lg:flex items-center bg-slate-50 border border-slate-200 rounded-2xl px-5 py-3 text-slate-400 focus-within:ring-2 ring-indigo-100 transition-all">
          <Search size={18} />
          <input
            className="bg-transparent border-none outline-none ml-4 text-sm font-medium w-48"
            placeholder="Quick search vault..."
          />
        </div>
        {onUploadClick && (
          <Button onClick={onUploadClick} className="px-8 py-4 shadow-2xl">
            <Upload size={20} /> Ingest PDF
          </Button>
        )}
      </div>
    </header>
  );
};
