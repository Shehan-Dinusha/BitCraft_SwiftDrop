import { ReactNode } from 'react';
import { ModalDrawerShell, type ModalDrawerSize } from '@/design-system/components/ModalDrawerShell/ModalDrawerShell';

export interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  description?: string;
  size?: ModalDrawerSize;
}

export function Modal({ open, onClose, title, children, footer, description, size }: ModalProps) {
  return (
    <ModalDrawerShell
      mode="modal"
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
