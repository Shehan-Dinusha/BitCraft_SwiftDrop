import { ReactNode } from 'react';
import { ModalDrawerShell, type ModalDrawerSize } from '@/design-system/components/ModalDrawerShell/ModalDrawerShell';

export interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  description?: string;
  size?: ModalDrawerSize;
}

export function Drawer({ open, onClose, title, children, footer, description, size }: DrawerProps) {
  return (
    <ModalDrawerShell
      mode="drawer"
      open={open}
      onClose={onClose}
      title={title}
      footer={footer}
      description={description}
      size={size}
    >
      {children}
    </ModalDrawerShell>
  );
}
