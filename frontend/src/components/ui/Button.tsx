import type { ButtonHTMLAttributes, ReactNode } from 'react';

import { Loader2 } from 'lucide-react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  variant?: ButtonVariant;
  className?: string;
  loading?: boolean;
};

const Button = ({
  children,
  variant = 'primary',
  className = '',
  loading = false,
  ...props
}: ButtonProps) => {
  const themes: Record<ButtonVariant, string> = {
    primary:
      'bg-neutral-900 text-white hover:bg-black shadow-md shadow-neutral-300',
    secondary:
      'bg-white text-neutral-800 border border-neutral-300 hover:bg-neutral-100',
    ghost: 'text-neutral-700 hover:bg-neutral-100',
  };

  return (
    <button
      disabled={loading || props.disabled}
      className={`rounded-2xl px-5 py-2.5 text-sm font-bold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-400 flex items-center justify-center gap-2 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed ${themes[variant]} ${className}`}
      {...props}
    >
      {loading ? <Loader2 size={18} className="animate-spin" /> : children}
    </button>
  );
};

export default Button;
