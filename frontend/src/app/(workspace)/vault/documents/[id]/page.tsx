import { DocumentDetailClient } from '@/features/documents/DocumentDetailClient';

interface Props {
  params: Promise<{ id: string }>;
}

export default async function DocumentDetailPage({ params }: Props) {
  const { id } = await params;
  return <DocumentDetailClient id={id} />;
}
