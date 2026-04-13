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
          ? 'bg-neutral-900 text-white border-neutral-900'
          : status === 'FAILED'
            ? 'bg-neutral-200 text-neutral-900 border-neutral-300'
            : 'bg-white text-neutral-700 border-neutral-300'
      } ${isPending ? 'animate-pulse' : ''}`}
    >
      {status}
    </span>
  );
};

export default StatusBadge;
