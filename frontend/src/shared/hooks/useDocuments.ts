'use client';

import { useAuth } from '@/context/AuthContext';
import { apiCall, ApiError } from '@/shared/lib/api';
import type { DocumentSummaryResponse } from '@/shared/types';
import { useCallback, useEffect, useState } from 'react';

const POLL_INTERVAL = 4000;

export function useDocuments({ enabled = true, pollProcessing = false } = {}) {
  const { token } = useAuth();
  const [documents, setDocuments] = useState<DocumentSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const refetch = useCallback(async () => {
    if (!enabled || !token) return;
    try {
      const data = await apiCall<DocumentSummaryResponse[]>('/documents', undefined, token);
      setDocuments(data ?? []);
      setError('');
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [enabled, token]);

  useEffect(() => {
    refetch();
  }, [refetch]);

  useEffect(() => {
    if (!pollProcessing) return;
    const hasProcessing = documents.some(
      (doc) => doc.status === 'PROCESSING' || doc.status === 'PENDING'
    );
    if (!hasProcessing) return;

    const timer = setInterval(refetch, POLL_INTERVAL);
    return () => clearInterval(timer);
  }, [documents, pollProcessing, refetch]);

  return {
    documents,
    setDocuments,
    loading,
    error,
    setError,
    refetch,
  };
}
