import { Briefcase } from 'lucide-react';

const EmptyVault = () => (
  <div className="group flex h-[60vh] flex-col items-center justify-center text-center opacity-80 transition-all">
    <div className="mb-8 rounded-full bg-neutral-200 p-12 transition-transform group-hover:scale-105">
      <Briefcase size={72} className="text-neutral-500" />
    </div>
    <h3 className="text-3xl font-black tracking-tight text-neutral-900">
      No Documents Yet
    </h3>
    <p className="mt-4 max-w-sm font-medium leading-relaxed text-neutral-600">
      Upload your first document to build the retrieval index and start asking
      grounded questions.
    </p>
  </div>
);

export default EmptyVault;
