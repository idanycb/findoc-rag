'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import Fuse from 'fuse.js';
import { Search, Upload } from 'lucide-react';
import Button from '@/components/ui/Button';
import { Document } from '@/lib/types';

interface MainHeaderProps {
  title: string;
  subtitle?: string;
  onUploadClick?: () => void;
  documents?: Document[];
  onSelectDocument?: (doc: Document) => void;
}

export const MainHeader: React.FC<MainHeaderProps> = ({
  title,
  subtitle = 'General-purpose retrieval workspace',
  onUploadClick,
  documents = [],
  onSelectDocument,
}) => {
  const [query, setQuery] = useState('');
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const searchContainerRef = useRef<HTMLDivElement>(null);

  const fuse = useMemo(
    () =>
      new Fuse(documents, {
        keys: ['fileName'],
        threshold: 0.35,
        ignoreLocation: true,
      }),
    [documents]
  );

  const matchingDocuments = useMemo(() => {
    const trimmedQuery = query.trim();

    if (!trimmedQuery) {
      return [] as Document[];
    }

    return fuse.search(trimmedQuery).map((result) => result.item);
  }, [fuse, query]);

  const showSearchResults = isSearchFocused && query.trim().length > 0;

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        searchContainerRef.current &&
        !searchContainerRef.current.contains(event.target as Node)
      ) {
        setIsSearchFocused(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);

    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleDocumentSelect = (doc: Document) => {
    onSelectDocument?.(doc);
    setQuery('');
    setIsSearchFocused(false);
  };

  return (
    <header className="sticky top-0 z-20 border-b border-neutral-200 bg-white/85 backdrop-blur-xl">
      <div className="flex min-h-20 flex-col gap-4 px-4 py-4 sm:px-6 lg:min-h-24 lg:flex-row lg:items-center lg:justify-between lg:px-10">
        <div>
          <h2 className="text-2xl font-black tracking-tight text-neutral-900 capitalize sm:text-3xl">
            {title}
          </h2>
          <p className="mt-1 text-[10px] font-black uppercase tracking-widest text-neutral-500 sm:text-[11px]">
            {subtitle}
          </p>
        </div>

        <div className="flex w-full items-center gap-3 lg:w-auto lg:gap-4">
          <div ref={searchContainerRef} className="relative hidden md:block">
            <div className="flex items-center rounded-2xl border border-neutral-300 bg-neutral-100 px-4 py-2 text-neutral-500 transition-all focus-within:ring-2 focus-within:ring-neutral-400">
              <Search size={18} />
              <input
                className="ml-3 w-40 border-none bg-transparent text-sm font-medium outline-none lg:w-48"
                placeholder="Search documents..."
                value={query}
                onFocus={() => setIsSearchFocused(true)}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Escape') {
                    setIsSearchFocused(false);
                  }

                  if (event.key === 'Enter' && matchingDocuments.length > 0) {
                    handleDocumentSelect(matchingDocuments[0]);
                  }
                }}
              />
            </div>

            {showSearchResults && (
              <div className="absolute top-[calc(100%+0.5rem)] z-30 w-full overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-2xl">
                {matchingDocuments.length > 0 ? (
                  <ul className="max-h-80 overflow-y-auto py-1">
                    {matchingDocuments.map((doc) => (
                      <li key={doc.id}>
                        <button
                          type="button"
                          onClick={() => handleDocumentSelect(doc)}
                          className="flex w-full items-center justify-between px-4 py-2 text-left transition-colors hover:bg-neutral-100"
                        >
                          <span className="truncate text-sm font-semibold text-neutral-900">
                            {doc.fileName}
                          </span>
                          <span className="ml-3 shrink-0 text-[10px] font-black uppercase tracking-widest text-neutral-500">
                            {doc.status}
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="px-4 py-3 text-sm font-medium text-neutral-500">
                    No matching documents
                  </p>
                )}
              </div>
            )}
          </div>
          {onUploadClick && (
            <Button
              onClick={onUploadClick}
              className="ml-auto w-full px-6 py-3 sm:w-auto"
            >
              <Upload size={18} /> Upload Document
            </Button>
          )}
        </div>
      </div>
    </header>
  );
};
