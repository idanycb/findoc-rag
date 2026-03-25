import { Briefcase } from 'lucide-react';

const EmptyVault = () => (
  <div className="h-[60vh] flex flex-col items-center justify-center text-center opacity-60 filter grayscale group transition-all hover:grayscale-0">
    <div className="bg-slate-100 p-14 rounded-full mb-10 transition-transform group-hover:scale-110">
      <Briefcase size={80} className="text-slate-300" />
    </div>
    <h3 className="text-3xl font-black text-slate-800 tracking-tight">
      Vault Offline
    </h3>
    <p className="text-slate-400 mt-4 max-w-sm font-medium leading-relaxed">
      System metadata initialized. Awaiting first financial report ingestion to
      trigger vector synthesis.
    </p>
  </div>
);

export default EmptyVault;
