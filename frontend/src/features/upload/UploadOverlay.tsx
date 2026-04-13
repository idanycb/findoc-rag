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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-md sm:p-6">
      <Card className="w-full max-w-md border-white/20 p-7 animate-in zoom-in-95 shadow-3xl sm:p-10">
        <h3 className="mb-6 text-2xl font-black text-neutral-900 sm:mb-8">
          Upload Document
        </h3>

        <label className="group mb-8 flex h-56 cursor-pointer flex-col items-center justify-center rounded-[36px] border-4 border-dashed border-neutral-300 transition-all hover:border-neutral-500 hover:bg-neutral-100 sm:mb-10 sm:h-64">
          <input
            type="file"
            className="hidden"
            accept=".pdf"
            onChange={(e) => {
              setFile(e.target.files?.[0] ?? null);
              setError(null);
            }}
          />
          <div className="rounded-full bg-neutral-200 p-4 text-neutral-700 transition-transform group-hover:scale-110">
            <FileUp size={48} />
          </div>
          <p className="mt-4 text-sm font-bold text-neutral-600">
            {file ? file.name : 'Select PDF Document'}
          </p>
        </label>

        {error && (
          <p className="mb-4 rounded-xl border border-neutral-300 bg-neutral-100 px-3 py-2 text-sm font-semibold text-neutral-700">
            {error}
          </p>
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
            {loading ? 'Uploading...' : 'Upload'}
          </Button>
        </div>
      </Card>
    </div>
  );
};
