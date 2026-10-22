import { InputHTMLAttributes, forwardRef } from 'react';
import { cn } from '@/utils/cn';

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(({ className, ...props }, ref) => (
  <input ref={ref} className={cn('min-h-10 w-full rounded-lg border border-subtle bg-card px-3 text-sm text-primary outline-none placeholder:text-muted focus:border-focus focus:ring-2 focus:ring-blue-100', className)} {...props} />
));
Input.displayName = 'Input';
