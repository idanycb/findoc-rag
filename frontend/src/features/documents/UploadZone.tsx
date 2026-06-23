'use client';

import { useRef, useState, useCallback } from 'react';
import { Upload } from 'lucide-react';

interface UploadZoneProps {
  onUpload: (file: File) => void;
  uploading?: boolean;
}

export function UploadZone({ onUpload, uploading }: UploadZoneProps) {
  const [dragOver, setDragOver] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragOver(false);
      const file = e.dataTransfer.files[0];
      if (file) onUpload(file);
    },
    [onUpload]
  );

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setDragOver(true);
  };

  const handleDragLeave = () => setDragOver(false);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      onUpload(file);
      e.target.value = '';
    }
  };

  return (
    <div
      onClick={() => !uploading && inputRef.current?.click()}
      onDrop={handleDrop}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      className={`border-[1.5px] border-dashed rounded-[14px] bg-white p-[26px] flex flex-col items-center text-center cursor-pointer shadow-[0_1px_4px_rgba(0,0,0,.06)] transition-colors ${
        dragOver
          ? 'border-[#111111] bg-[#F7F7F7]'
          : 'border-[#E5E5E5] hover:border-[#BBBBBB]'
      } ${uploading ? 'pointer-events-none opacity-60' : ''}`}
    >
      <div className="w-12 h-12 rounded-[12px] bg-[#F5F5F5] flex items-center justify-center">
        <Upload size={22} className="text-[#111111]" />
      </div>
      <p className="text-[15px] font-semibold text-[#111111] mt-[14px]">
        {uploading ? 'Uploading…' : 'Click to upload or drag and drop'}
      </p>
      <p className="text-[12px] text-[#AAAAAA] mt-[6px]">
        PDF, TXT, MD or DOCX · max 10MB
      </p>
      <input
        ref={inputRef}
        type="file"
        accept=".pdf,.txt,.md,.docx,application/pdf,text/plain,text/markdown,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        className="hidden"
        onChange={handleFileChange}
      />
    </div>
  );
}
