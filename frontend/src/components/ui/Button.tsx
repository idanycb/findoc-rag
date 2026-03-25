import type { ButtonHTMLAttributes, ReactNode } from 'react';

import { Loader2 } from 'lucide-react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  children: ReactNode;
  variant?: ButtonVariant;
  className?: string;
  loading?: boolean;
};

const Button = ({ children, variant = 'primary', className = '', loading = false, ...props }:ButtonProps) => {
  const themes : Record<ButtonVariant, string> = {
    primary: 'bg-indigo-600 text-white hover:bg-indigo-700 shadow-md shadow-indigo-100',
    secondary: 'bg-white text-slate-700 border border-slate-200 hover:bg-slate-50',
    ghost: 'text-slate-500 hover:bg-slate-100'
  };

  return (
    <button 
      disabled={loading || props.disabled}
      className={`px-5 py-2.5 rounded-2xl font-bold text-sm transition-all flex items-center justify-center gap-2 active:scale-95 disabled:opacity-50 ${themes[variant]} ${className}`}
      {...props}
    >
      {loading ? <Loader2 size={18} className="animate-spin" /> : children}
    </button>
  );
};
export default Button;