import { useCallback, useEffect, useState } from 'react';
import { RefreshCw, Wallet } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { Modal } from '@/design-system/primitives/Modal';
import { CashState } from '@/design-system/components/CashState/CashState';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { cashTime, handoverState, lkr } from '@/features/cash/cashDisplay';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { cashApi, type CourierCashResponse } from '@/services/api/cashApi';

/** The cash the courier holds, what it came from, and handing it to their dispatcher. */
export function CourierCashPage() {
  const [cash, setCash] = useState<CourierCashResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCash(await cashApi.mine());
      setError('');
    } catch (err) {
      setError(apiErrorMessage(err, 'Your cash could not be loaded.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    setMessage('');
    try {
      const handover = await cashApi.submitHandover();
      setMessage(`${lkr(handover.expectedAmount)} submitted. Give it to your dispatcher to count.`);
    } catch (err) {
      setError(apiErrorMessage(err, 'The cash could not be submitted.'));
    } finally {
      setSubmitting(false);
      setConfirming(false);
    }
    try {
      setCash(await cashApi.mine());
    } catch {
      // The submit result above still stands; Refresh loads the list again.
    }
  };

  return (
    <main className="mx-auto max-w-3xl space-y-5 p-4 md:p-6" data-density="kinetic">
      <header className="flex items-center justify-between gap-3">
        <h1 className="text-h1 text-primary">Cash</h1>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw
            aria-hidden="true"
            className={`mr-2 size-4 ${loading ? 'animate-spin' : ''}`}
          />
          Refresh
        </Button>
      </header>

      {error && (
        <StatusBanner tone="danger" title="Cash" onDismiss={() => setError('')}>
          {error}
        </StatusBanner>
      )}
      {message && (
        <StatusBanner tone="success" title="Submitted" onDismiss={() => setMessage('')}>
          {message}
        </StatusBanner>
      )}

      {cash && (
        <>
          <SurfaceCard>
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <span className="grid size-11 shrink-0 place-items-center rounded-lg bg-brand-subtle text-brand-primary">
                  <Wallet aria-hidden="true" className="size-5" />
                </span>
                <div>
                  <h2 className="text-label text-secondary">Cash in hand</h2>
                  <MoneyAmount amount={cash.inHand} currency={cash.currency} size="lg" />
                </div>
              </div>
              <Button
                variant="primary"
                className="min-h-target w-full"
                disabled={cash.inHand <= 0 || submitting}
                onClick={() => setConfirming(true)}
              >
                Hand over to the dispatcher
              </Button>
            </div>
          </SurfaceCard>

          <section className="space-y-3">
            <h2 className="text-h3 text-primary">Collected</h2>
            {cash.entries.length === 0 ? (
              <p className="text-body text-secondary">No cash to hand over.</p>
            ) : (
              <ul className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
                {cash.entries.map((entry) => (
                  <li
                    key={entry.entryId}
                    className="flex min-h-target items-center justify-between gap-3 px-4 py-2"
                  >
                    <div className="min-w-0">
                      <p className="truncate font-mono text-body-strong tabular-nums text-primary">
                        {entry.trackingNumber}
                      </p>
                      <p className="text-caption text-secondary">{cashTime(entry.recordedAt)}</p>
                    </div>
                    <MoneyAmount amount={entry.amount} currency={entry.currency} />
                  </li>
                ))}
              </ul>
            )}
          </section>

          {cash.handovers.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-h3 text-primary">Handed over</h2>
              <ul className="divide-y divide-border-subtle rounded-xl border border-border-subtle bg-card">
                {cash.handovers.map((handover) => (
                  <li key={handover.handoverId} className="space-y-1 px-4 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <MoneyAmount amount={handover.expectedAmount} currency={handover.currency} />
                      <CashState {...handoverState(handover)} />
                    </div>
                    <p className="text-caption text-secondary">{cashTime(handover.submittedAt)}</p>
                    {handover.note && (
                      <p className="break-words text-body text-primary">{handover.note}</p>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      <Modal
        open={confirming}
        onClose={() => {
          if (!submitting) setConfirming(false);
        }}
        title="Hand over your cash"
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="outline" disabled={submitting} onClick={() => setConfirming(false)}>
              Cancel
            </Button>
            <Button variant="primary" loading={submitting} onClick={() => void submit()}>
              Submit
            </Button>
          </div>
        }
      >
        <p className="text-body text-primary">
          You are handing over {lkr(cash?.inHand ?? 0)}. Your dispatcher counts it, and any
          difference is recorded.
        </p>
      </Modal>
    </main>
  );
}
