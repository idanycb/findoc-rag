'use client';

import { useState, useEffect, useCallback } from 'react';
import { apiCall } from '@/lib/api';
import { Document } from '@/lib/types';

export const useDocuments = (token: string) => {
  const [documents, setDocuments] = useState<Document[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchDocument = useCallback(
    async (docId: string) => {
      if (!token) return;

      try {
        const data = await apiCall(`/documents/${docId}`, {}, token);
        setDocuments((prevDocs) =>
          prevDocs.map((doc) => (doc.id === docId ? data : doc))
        );
      } catch (err) {
        console.error(`Failed to fetch document ${docId}:`, err);
      }
    },
    [token]
  );

  const fetchDocuments = useCallback(async () => {
    if (!token) {
      setDocuments([]);
      setError(null);
      setLoading(false);
      return;
    }

    try {
      setLoading(true);
      setError(null);
      const data = await apiCall('/documents', {}, token);
      setDocuments(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : 'Failed to fetch documents'
      );
      console.error('Failed to fetch documents:', err);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments]);

  useEffect(() => {
    if (documents.length === 0) return;

    const processingDocumentIds = documents
      .filter((doc) => doc.status === 'PROCESSING' || doc.status === 'PENDING')
      .map((doc) => doc.id);

    if (processingDocumentIds.length === 0) {
      return;
    }

    const interval = setInterval(() => {
      processingDocumentIds.forEach((docId) => {
        fetchDocument(docId);
      });
    }, 2000);

    return () => clearInterval(interval);
  }, [documents, fetchDocument]);

  return { documents, loading, error, refetch: fetchDocuments };
};

export const useDocumentInsights = (docId: string, token: string) => {
  const [viewUrl, setViewUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!docId || !token) return;

    const fetchInsights = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await apiCall(`/documents/${docId}/view`, {}, token);
        setViewUrl(data.viewUrl);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : 'Failed to fetch insights'
        );
        console.error('Failed to fetch insights:', err);
      } finally {
        setLoading(false);
      }
    };

    fetchInsights();
  }, [docId, token]);

  return { viewUrl, loading, error };
};
