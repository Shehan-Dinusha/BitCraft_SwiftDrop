import { forwardRef, type ReactNode } from 'react';
import { cn } from '@/utils/cn';
import { Button, type ButtonProps } from './Button';

export interface IconButtonProps extends Omit<ButtonProps, 'leftIcon' | 'rightIcon' | 'children'> {
  /** The icon to show (e.g. a `lucide-react` element). */
  icon: ReactNode;
  /** Required: an icon-only control has no visible text for assistive tech. */
  'aria-label': string;
}

/** Square icon-only button. Same five variants and density sizes as `Button` (Figma: 44×44 comfortable). */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ icon, className, ...props }, ref) => (
    <Button
      ref={ref}
      leftIcon={icon}
      className={cn('aspect-square px-0 py-0', className)}
      {...props}
    />
  )
);
IconButton.displayName = 'IconButton';
