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
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8 animate-in fade-in slide-in-from-bottom-4 duration-700">
      {documents.map((doc) => (
        <Card
          key={doc.id}
          onClick={() => onSelectDoc(doc)}
          className="group relative"
        >
          <div className="flex justify-between items-start mb-6">
            <div className="p-4 bg-slate-50 text-slate-400 rounded-2xl group-hover:bg-indigo-50 group-hover:text-indigo-600 transition-colors">
              <FileText size={32} />
            </div>
            <StatusBadge status={doc.status} />
          </div>
          <h4 className="font-bold text-slate-800 text-lg mb-1 truncate group-hover:text-indigo-600 transition-colors">
            {doc.fileName}
          </h4>
          <p className="text-xs text-slate-400 font-medium tracking-tight">
            Archived on {new Date(doc.uploadedAt).toLocaleDateString()}
          </p>
          <div className="mt-10 pt-4 border-t border-slate-50 flex items-center justify-between text-[10px] font-black text-slate-400 tracking-[0.2em] uppercase">
            <span>Audit Context</span>
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
