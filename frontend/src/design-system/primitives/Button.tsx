import { ButtonHTMLAttributes, forwardRef } from 'react';
import { cn } from '@/utils/cn';

export const Button = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' }>(
  ({ className, variant = 'primary', ...props }, ref) => (
    <button
      ref={ref}
      className={cn(
        'inline-flex min-h-10 items-center justify-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-border-focus disabled:cursor-not-allowed disabled:opacity-50',
        variant === 'primary' && 'bg-brand-primary text-brand-on hover:bg-brand-hover active:bg-brand-pressed',
        variant === 'secondary' && 'border border-subtle bg-card text-primary hover:bg-canvas',
        variant === 'ghost' && 'text-secondary hover:bg-canvas',
        variant === 'danger' && 'bg-status-danger text-white hover:opacity-90',
        className
      )}
      {...props}
    />
  )
);
Button.displayName = 'Button';
