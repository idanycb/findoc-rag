import { apiCall, ApiError } from '@/shared/lib/api';
import type { UploadResult } from '@/shared/types';

export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

const MIME_BY_EXTENSION: Record<string, string> = {
  pdf: 'application/pdf',
  txt: 'text/plain',
  md: 'text/markdown',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
};

export function resolveContentType(file: File): string {
  if (file.type) return file.type;

  const extension = file.name.split('.').pop()?.toLowerCase() ?? '';
  const mime = MIME_BY_EXTENSION[extension];
  if (!mime) {
    throw new ApiError(400, `Unsupported file type: .${extension || 'unknown'}`);
  }
  return mime;
}

async function uploadToPresignedUrl(
  uploadUrl: string,
  file: File,
  contentType: string
): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: file,
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => '');
    throw new ApiError(
      response.status,
      detail.trim()
        ? `S3 upload failed: ${detail}`
        : `S3 upload failed (${response.status} ${response.statusText})`
    );
  }
}

/**
 * Two-step document upload:
 * 1. POST /documents → presigned S3 PUT URL
 * 2. PUT file bytes directly to S3 with matching Content-Type
 * 3. POST /documents/{id}/analyze (required in local/dev)
 */
export async function uploadDocument(file: File, token: string): Promise<string> {
  if (file.size > MAX_UPLOAD_BYTES) {
    throw new ApiError(400, 'File is too large. Upload documents up to 10 MB.');
  }

  const contentType = resolveContentType(file);

  const init = await apiCall<UploadResult>(
    '/documents',
    {
      method: 'POST',
      body: JSON.stringify({
        fileName: file.name,
        size: file.size,
        type: contentType,
      }),
    },
    token
  );

  if (!init?.uploadUrl || !init.documentId) {
    throw new ApiError(500, 'Upload ticket missing presigned URL or document ID.');
  }

  await uploadToPresignedUrl(init.uploadUrl, file, contentType);

  await apiCall(`/documents/${init.documentId}/analyze`, { method: 'POST' }, token);

  return init.documentId;
}

export async function fetchViewUrl(documentId: string, token: string): Promise<string> {
  const data = await apiCall<{ viewUrl: string }>(
    `/documents/${documentId}/view`,
    undefined,
    token
  );

  if (!data?.viewUrl) {
    throw new ApiError(500, 'View URL missing from response.');
  }

  return data.viewUrl;
}
