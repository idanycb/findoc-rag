'use client';

import React, { useState } from 'react';
import { FileUp } from 'lucide-react';
import Card from '@/components/ui/Card';
import Button from '@/components/ui/Button';
import { apiCall } from '@/lib/api';

interface UploadOverlayProps {
  token: string;
  onClose: () => void;
  onComplete: () => void;
}

export const UploadOverlay: React.FC<UploadOverlayProps> = ({
  token,
  onClose,
  onComplete,
}) => {
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleIngest = async () => {
    if (!file) return;

    try {
      setLoading(true);
      setError(null);

      // Step 1: Get presigned S3 URL
      const { uploadUrl } = await apiCall(
        '/documents',
        {
          method: 'POST',
          body: JSON.stringify({
            fileName: file.name,
            size: file.size,
            type: file.type,
          }),
        },
        token
      );

      // Step 2: Upload to S3
      const s3Res = await fetch(uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type },
        body: file,
      });

      if (!s3Res.ok) {
        throw new Error('S3 upload failed');
      }

      onComplete();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
      console.error('Upload error:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-md flex items-center justify-center z-50 p-6">
      <Card className="max-w-md w-full p-12 animate-in zoom-in-95 shadow-3xl border-white/20">
        <h3 className="text-2xl font-black mb-8 text-slate-800">
          Ingest Document
        </h3>

        <label className="border-4 border-dashed border-slate-100 rounded-[40px] h-64 flex flex-col items-center justify-center cursor-pointer hover:bg-slate-50 hover:border-indigo-300 transition-all mb-10 group">
          <input
            type="file"
            className="hidden"
            accept=".pdf"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null);
              setError(null);
            }}
          />
          <div className="bg-indigo-50 text-indigo-400 p-4 rounded-full group-hover:scale-110 transition-transform">
            <FileUp size={48} />
          </div>
          <p className="text-sm font-bold text-slate-500 mt-4">
            {file ? file.name : 'Select Financial PDF'}
          </p>
        </label>

        {error && (
          <p className="text-sm text-rose-600 font-semibold mb-4">{error}</p>
        )}

        <div className="flex gap-4">
          <Button variant="ghost" className="flex-1" onClick={onClose}>
            Discard
          </Button>
          <Button
            className="flex-1"
            onClick={handleIngest}
            disabled={loading || !file}
          >
            {loading ? 'Uploading...' : 'Begin Archival'}
          </Button>
        </div>
      </Card>
    </div>
  );
};
