import { AlertTriangle, ArrowRightLeft, CheckCircle2, Clock, PlusCircle } from 'lucide-react';
import { cn } from '@/utils/cn';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';

export type CashStateKind = 'pending' | 'collected' | 'handed-over' | 'shortage' | 'excess';

export interface CashStateProps {
  state: CashStateKind;
  /** Amount in LKR. */
  amount: number;
  className?: string;
}

const states: Record<CashStateKind, { label: string; tone: string; Icon: typeof Clock }> = {
  pending: {
    label: 'Cash pending',
    tone: 'border-fsm-in-transit-border bg-fsm-in-transit-bg text-fsm-in-transit-fg',
    Icon: Clock
  },
  collected: {
    label: 'Collected',
    tone: 'border-fsm-delivered-border bg-fsm-delivered-bg text-fsm-delivered-fg',
    Icon: CheckCircle2
  },
  'handed-over': {
    label: 'Handed over',
    tone: 'border-fsm-picked-up-border bg-fsm-picked-up-bg text-fsm-picked-up-fg',
    Icon: ArrowRightLeft
  },
  shortage: {
    label: 'Shortage',
    tone: 'border-fsm-failed-border bg-fsm-failed-bg text-fsm-failed-fg',
    Icon: AlertTriangle
  },
  excess: {
    label: 'Excess',
    tone: 'border-fsm-created-border bg-fsm-created-bg text-fsm-created-fg',
    Icon: PlusCircle
  }
};

/** Figma CashState: cash-on-delivery status pill (pending, collected, handed over, shortage, excess). */
export function CashState({ state, amount, className }: CashStateProps) {
  const { label, tone, Icon } = states[state];
  return (
    <span
      data-state={state}
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-label',
        tone,
        className
      )}
    >
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {label}
      <MoneyAmount amount={amount} size="sm" inheritColor />
    </span>
  );
}

export default CashState;
