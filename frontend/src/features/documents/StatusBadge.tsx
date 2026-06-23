import type { DocumentStatus } from '@/shared/types';

const STATUS_CONFIG: Record<
  DocumentStatus,
  { label: string; text: string; bg: string; border?: string }
> = {
  COMPLETED: { label: 'COMPLETED', text: '#ffffff', bg: '#111111' },
  PROCESSING: { label: 'PROCESSING', text: '#555555', bg: '#E8E8E8' },
  PENDING: { label: 'PENDING', text: '#999999', bg: '#F0F0F0', border: '#E5E5E5' },
  FAILED: { label: 'FAILED', text: '#B91C1C', bg: '#FEE2E2' },
};

export function StatusBadge({ status }: { status: DocumentStatus }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.PENDING;
  return (
    <span
      className="inline-flex items-center rounded-full px-[10px] py-1 text-[10.5px] font-bold tracking-[.05em]"
      style={{ color: cfg.text, background: cfg.bg, border: cfg.border ? `1px solid ${cfg.border}` : undefined }}
    >
      {cfg.label}
    </span>
  );
}
