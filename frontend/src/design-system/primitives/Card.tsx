import { HTMLAttributes } from 'react';
import { cn } from '@/utils/cn';

export const Card = ({ className, ...props }: HTMLAttributes<HTMLDivElement>) => (
  <section className={cn('rounded-2xl border border-subtle bg-card p-5 text-primary shadow-sm', className)} {...props} />
);
