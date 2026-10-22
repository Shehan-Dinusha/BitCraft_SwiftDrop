import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';
import { ReactNode } from 'react';

type StatusTone = 'success' | 'warning' | 'danger' | 'info';

const styles: Record<StatusTone, { className: string; icon: ReactNode }> = {
  success: { className: 'border-emerald-200 bg-emerald-50 text-emerald-800', icon: <CheckCircle2 size={18} /> },
  warning: { className: 'border-amber-200 bg-amber-50 text-amber-800', icon: <TriangleAlert size={18} /> },
  danger: { className: 'border-red-200 bg-red-50 text-red-800', icon: <AlertCircle size={18} /> },
  info: { className: 'border-sky-200 bg-sky-50 text-sky-800', icon: <Info size={18} /> }
};

export function StatusBanner({ tone, title, children }: { tone: StatusTone; title: string; children?: ReactNode }) {
  const style = styles[tone];
  return (
    <div role={tone === 'danger' ? 'alert' : 'status'} className={`flex gap-3 rounded-lg border p-3 text-sm ${style.className}`}>
      <span aria-hidden>{style.icon}</span>
      <div><p className="font-semibold">{title}</p>{children && <p className="mt-1 opacity-90">{children}</p>}</div>
    </div>
  );
}
