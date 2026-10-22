import { ReactNode } from 'react';
import { Button } from './Button';

export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  if (!open) return null;
  return <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4">
    <div className="w-full max-w-lg rounded-2xl bg-white p-5">
      <div className="mb-4 flex justify-between"><h2 className="font-bold">{title}</h2><Button variant="ghost" onClick={onClose}>Close</Button></div>
      {children}
    </div>
  </div>;
}
