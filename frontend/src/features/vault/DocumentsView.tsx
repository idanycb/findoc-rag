import Card from '@/components/ui/Card';
import { ChevronRight, FileText } from 'lucide-react';
import EmptyVault from './EmptyVault';
import StatusBadge from './StatusBadge';
import { StatusType } from './types';

interface Document {
  id: string;
  fileName: string;
  uploadedAt: string | number | Date;
  status: StatusType;
  size?: number;
  type?: string;
  aiSummary?: string;
}

type DocumentsViewProps = {
  documents: Document[];
  onSelectDoc: (doc: Document) => void;
};

const DocumentsView = ({ documents, onSelectDoc }: DocumentsViewProps) => {
  if (!documents.length) return <EmptyVault />;
  return (
    <div className="grid grid-cols-1 gap-4 animate-in fade-in slide-in-from-bottom-4 duration-700 sm:gap-6 md:grid-cols-2 lg:grid-cols-3">
      {documents.map((doc) => (
        <Card
          key={doc.id}
          onClick={() => onSelectDoc(doc)}
          className="group relative"
        >
          <div className="flex justify-between items-start mb-6">
            <div className="rounded-2xl bg-neutral-100 p-4 text-neutral-500 transition-colors group-hover:bg-neutral-900 group-hover:text-white">
              <FileText size={32} />
            </div>
            <StatusBadge status={doc.status} />
          </div>
          <h4 className="mb-1 truncate text-lg font-bold text-neutral-900 transition-colors group-hover:text-black">
            {doc.fileName}
          </h4>
          <p className="text-xs font-medium tracking-tight text-neutral-500">
            Indexed on {new Date(doc.uploadedAt).toLocaleDateString()}
          </p>
          <div className="mt-8 flex items-center justify-between border-t border-neutral-200 pt-4 text-[10px] font-black uppercase tracking-[0.2em] text-neutral-500">
            <span>View Details</span>
            <ChevronRight
              size={16}
              className="group-hover:translate-x-1 transition-transform"
            />
          </div>
        </Card>
      ))}
    </div>
  );
};

export default DocumentsView;
