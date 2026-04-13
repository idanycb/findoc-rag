import type { MouseEventHandler, ReactNode } from 'react';

type CardProps = {
  children: ReactNode;
  className?: string;
  onClick?: MouseEventHandler<HTMLDivElement>;
};

const Card = ({ children, className = '', onClick }: CardProps) => (
  <div
    onClick={onClick}
    className={`rounded-4xl border border-neutral-200 bg-white p-8 transition-all ${onClick ? 'cursor-pointer hover:border-neutral-400 hover:shadow-2xl' : ''} ${className}`}
  >
    {children}
  </div>
);

export default Card;
