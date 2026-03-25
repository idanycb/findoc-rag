import type { MouseEventHandler, ReactNode } from 'react';

type CardProps = {
  children: ReactNode;
  className?: string;
  onClick?: MouseEventHandler<HTMLDivElement>;
};

const Card = ({ children, className = '', onClick }: CardProps) => (
  <div 
    onClick={onClick}
    className={`bg-white border border-slate-100 rounded-4xl p-8 transition-all ${onClick ? 'cursor-pointer hover:shadow-2xl hover:border-indigo-100' : ''} ${className}`}
  >
    {children}
  </div>
);

export default Card;