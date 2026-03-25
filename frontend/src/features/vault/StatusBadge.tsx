import { StatusType } from './types';

interface StatusBadgeProps {
  status: StatusType;
}

const StatusBadge = ({ status }: StatusBadgeProps) => {
  const isPending = status === 'PENDING' || status === 'PROCESSING';
  return (
    <span
      className={`px-3 py-1 rounded-full text-[10px] font-black uppercase tracking-widest border ${
        status === 'COMPLETED'
          ? 'bg-emerald-50 text-emerald-600 border-emerald-100'
          : status === 'FAILED'
            ? 'bg-rose-50 text-rose-600 border-rose-100'
            : 'bg-amber-50 text-amber-600 border-amber-100'
      } ${isPending ? 'animate-pulse' : ''}`}
    >
      {status}
    </span>
  );
};

export default StatusBadge;
