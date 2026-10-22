import { ReactNode } from 'react';

export function Drawer({ open, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  return open ? <aside className="fixed inset-y-0 right-0 z-50 w-full max-w-md border-l border-subtle bg-elevated p-5 text-primary shadow-xl"><h2 className="font-bold">{title}</h2>{children}</aside> : null;
}
