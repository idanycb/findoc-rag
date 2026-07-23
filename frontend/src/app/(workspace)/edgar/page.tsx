import { EdgarBrowseClient } from '@/features/edgar/EdgarBrowseClient';

export const metadata = { title: 'SEC EDGAR · FinDoc Analyzer' };

export default function EdgarPage() {
  return <EdgarBrowseClient />;
}
