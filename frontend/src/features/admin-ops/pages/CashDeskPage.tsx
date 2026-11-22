import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Modal } from '@/design-system/primitives/Modal';
import { CashState } from '@/design-system/components/CashState/CashState';
import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
import { MoneyAmount } from '@/design-system/components/MoneyAmount/MoneyAmount';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { Tabs } from '@/design-system/components/Tabs/Tabs';
import { cashTime, handoverState, kindLabel, lkr, parseAmount } from '@/features/cash/cashDisplay';
import { districtLabel } from '@/features/profile/profileFormat';
import { SRI_LANKAN_DISTRICTS } from '@/services/api/authApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import {
  cashApi,
  type CashDepositResponse,
  type CashDeskResponse,
  type CashEntryResponse,
  type CashHandoverResponse,
  type SriLankanDistrict
} from '@/services/api/cashApi';

type TabId = 'count' | 'couriers' | 'hub' | 'deposits';
const TAB_IDS: TabId[] = ['count', 'couriers', 'hub', 'deposits'];

type Dialog =
  | { kind: 'count'; handover: CashHandoverResponse }
  | { kind: 'parcels'; handover: CashHandoverResponse }
  | { kind: 'deposit'; amount: number }
  | { kind: 'refund' }
  | { kind: 'confirm'; deposit: CashDepositResponse }
  | { kind: 'reject'; deposit: CashDepositResponse };

const DEPOSIT_LABEL: Record<CashDepositResponse['status'], string> = {
  SUBMITTED: 'To confirm',
  CONFIRMED: 'Confirmed',
  REJECTED: 'Rejected'
};

/**
 * The hub's cash, one tab per job. A dispatcher counts and banks their own district's cash; a system
 * admin sees every district and confirms the deposits.
 */
export function CashDeskPage() {
  const role = useAuthStore((s) => s.user?.role);
  const ownDistrict = useAuthStore((s) => s.user?.district);
  const isAdmin = role === 'SYSTEM_ADMIN';
  const canOpen = isAdmin || role === 'DISPATCHER';
  const [params, setParams] = useSearchParams();
  const requestedTab = params.get('tab') as TabId | null;
  const tab: TabId =
    requestedTab && TAB_IDS.includes(requestedTab) ? requestedTab : isAdmin ? 'deposits' : 'count';
  const [district, setDistrict] = useState<SriLankanDistrict | ''>('');
  const [desk, setDesk] = useState<CashDeskResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const latestLoad = useRef(0);

  const load = useCallback(async () => {
    if (!canOpen) return;
    const request = ++latestLoad.current;
    setLoading(true);
    try {
      const loaded = await cashApi.desk((isAdmin && district) || undefined);
      if (request !== latestLoad.current) return;
      setDesk(loaded);
      setError('');
    } catch (err) {
      if (request !== latestLoad.current) return;
      setDesk(null);
      setError(apiErrorMessage(err, 'The cash could not be loaded.'));
    } finally {
      if (request === latestLoad.current) setLoading(false);
    }
  }, [canOpen, isAdmin, district]);

  useEffect(() => {
    void load();
  }, [load]);

  const done = (text: string) => {
    setDialog(null);
    setMessage(text);
    void load();
  };

  if (!canOpen)
    return (
      <main className="p-6">
        <StatusBanner tone="warning" title="Operations access required" />
      </main>
    );

  const toConfirm = desk?.deposits.filter((deposit) => deposit.status === 'SUBMITTED').length;
  const money = (amount: number) => <MoneyAmount amount={amount} size="sm" />;

  return (
    <main className="space-y-4 p-4 md:p-6" data-density="compact">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-label text-secondary">
            {isAdmin ? 'All districts' : `${districtLabel(ownDistrict)} district`}
          </p>
          <h1 className="text-h1 text-primary">Cash</h1>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {isAdmin && (
            <label className="text-sm text-primary">
              District{' '}
              <select
                className="rounded-lg border border-border-subtle bg-card p-2"
                value={district}
                onChange={(e) => setDistrict(e.target.value as SriLankanDistrict | '')}
              >
                <option value="">All districts</option>
                {SRI_LANKAN_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {districtLabel(d)}
                  </option>
                ))}
              </select>
            </label>
          )}
          <Button variant="outline" disabled={loading} onClick={() => void load()}>
            Refresh
          </Button>
        </div>
      </header>

      {error && (
        <StatusBanner tone="danger" title="Cash" onDismiss={() => setError('')}>
          {error}
        </StatusBanner>
      )}
      {message && <StatusBanner tone="success" title={message} onDismiss={() => setMessage('')} />}

      <Tabs
        aria-label="Cash"
        value={tab}
        onChange={(id) => setParams({ tab: id }, { replace: true })}
        tabs={[
          { id: 'count', label: 'To count', count: desk?.toCount.length },
          { id: 'couriers', label: 'With couriers', count: desk?.withCouriers.length },
          { id: 'hub', label: 'At the hub', count: desk?.atHub.length },
          { id: 'deposits', label: 'Deposits', count: toConfirm }
        ]}
      />

      {!desk ? (
        loading && <p role="status">Loading cash…</p>
      ) : tab === 'count' ? (
        <HighDensityDataGrid
          density="compact"
          label="Handovers to count"
          filterPlaceholder="Search couriers"
          emptyMessage="Nothing to count"
          rows={desk.toCount.map((row) => ({ ...row, id: row.handoverId }))}
          columns={[
            { key: 'courierName', label: 'Courier' },
            ...(isAdmin ? [districtColumn<CashHandoverResponse & { id: string }>()] : []),
            { key: 'submittedAt', label: 'Submitted', render: (r) => cashTime(r.submittedAt) },
            {
              key: 'expectedAmount',
              label: 'Expected',
              align: 'right',
              render: (r) => money(r.expectedAmount)
            },
            {
              key: 'id',
              label: 'Action',
              render: (r) => (
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    onClick={() => setDialog({ kind: 'parcels', handover: r })}
                  >
                    Parcels
                  </Button>
                  {!isAdmin && (
                    <Button
                      variant="primary"
                      onClick={() => setDialog({ kind: 'count', handover: r })}
                    >
                      Count
                    </Button>
                  )}
                </div>
              )
            }
          ]}
        />
      ) : tab === 'couriers' ? (
        <HighDensityDataGrid
          density="compact"
          label="Cash with couriers"
          filterPlaceholder="Search couriers"
          emptyMessage="No courier holds cash"
          rows={desk.withCouriers.map((row) => ({ ...row, id: row.courierId }))}
          columns={[
            { key: 'courierName', label: 'Courier' },
            { key: 'parcels', label: 'Parcels', align: 'right' },
            { key: 'since', label: 'Oldest', render: (r) => cashTime(r.since) },
            { key: 'amount', label: 'Cash', align: 'right', render: (r) => money(r.amount) }
          ]}
        />
      ) : tab === 'hub' ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border-subtle bg-card p-4">
            <div>
              <h2 className="text-label text-secondary">Cash at the hub</h2>
              <MoneyAmount amount={desk.hubCash} size="lg" />
            </div>
            {!isAdmin && (
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" onClick={() => setDialog({ kind: 'refund' })}>
                  Refund a fee
                </Button>
                <Button
                  variant="primary"
                  disabled={desk.hubCash <= 0}
                  onClick={() => setDialog({ kind: 'deposit', amount: desk.hubCash })}
                >
                  Deposit
                </Button>
              </div>
            )}
          </div>
          <HighDensityDataGrid
            density="compact"
            label="Counted handovers at the hub"
            filterPlaceholder="Search couriers"
            emptyMessage="No counted cash at the hub"
            rows={desk.atHub.map((row) => ({ ...row, id: row.handoverId }))}
            columns={[
              { key: 'courierName', label: 'Courier' },
              ...(isAdmin ? [districtColumn<CashHandoverResponse & { id: string }>()] : []),
              { key: 'countedAt', label: 'Counted', render: (r) => cashTime(r.countedAt) },
              {
                key: 'countedAmount',
                label: 'Amount',
                align: 'right',
                render: (r) => money(r.countedAmount ?? 0)
              },
              {
                key: 'status',
                label: 'Result',
                render: (r) => <CashState {...handoverState(r)} />
              },
              { key: 'note', label: 'Note', render: (r) => r.note ?? '—' },
              {
                key: 'id',
                label: 'Action',
                render: (r) => (
                  <Button
                    variant="outline"
                    onClick={() => setDialog({ kind: 'parcels', handover: r })}
                  >
                    Parcels
                  </Button>
                )
              }
            ]}
          />
          {desk.refunds.length > 0 && (
            <section className="space-y-2">
              <h2 className="text-h3 text-primary">Refunds paid</h2>
              <EntryList entries={desk.refunds} />
            </section>
          )}
        </div>
      ) : (
        <HighDensityDataGrid
          density="compact"
          label="Deposits"
          filterPlaceholder="Search slip numbers"
          emptyMessage="No deposits yet"
          rows={desk.deposits.map((row) => ({ ...row, id: row.depositId }))}
          columns={[
            { key: 'reference', label: 'Bank slip' },
            ...(isAdmin ? [districtColumn<CashDepositResponse & { id: string }>()] : []),
            { key: 'depositedAt', label: 'Deposited', render: (r) => cashTime(r.depositedAt) },
            { key: 'amount', label: 'Amount', align: 'right', render: (r) => money(r.amount) },
            { key: 'status', label: 'Status', render: (r) => DEPOSIT_LABEL[r.status] },
            { key: 'reviewNote', label: 'Note', render: (r) => r.reviewNote ?? '—' },
            ...(isAdmin
              ? [
                  {
                    key: 'id' as const,
                    label: 'Action',
                    render: (r: CashDepositResponse & { id: string }) =>
                      r.status === 'SUBMITTED' ? (
                        <div className="flex gap-2">
                          <Button
                            variant="primary"
                            onClick={() => setDialog({ kind: 'confirm', deposit: r })}
                          >
                            Confirm
                          </Button>
                          <Button
                            variant="outline"
                            onClick={() => setDialog({ kind: 'reject', deposit: r })}
                          >
                            Reject
                          </Button>
                        </div>
                      ) : (
                        '—'
                      )
                  }
                ]
              : [])
          ]}
        />
      )}

      {dialog?.kind === 'count' && (
        <CountDialog
          key={dialog.handover.handoverId}
          handover={dialog.handover}
          onClose={() => setDialog(null)}
          onDone={done}
        />
      )}
      {dialog?.kind === 'parcels' && (
        <ParcelsDialog
          key={dialog.handover.handoverId}
          handover={dialog.handover}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'deposit' && (
        <DepositDialog amount={dialog.amount} onClose={() => setDialog(null)} onDone={done} />
      )}
      {dialog?.kind === 'refund' && <RefundDialog onClose={() => setDialog(null)} onDone={done} />}
      {dialog?.kind === 'confirm' && (
        <FormDialog
          title="Confirm deposit"
          submitLabel="Confirm"
          onClose={() => setDialog(null)}
          onSubmit={async () => {
            await cashApi.confirmDeposit(dialog.deposit.depositId);
            done('Deposit confirmed');
          }}
        >
          <p className="text-body text-primary">
            {lkr(dialog.deposit.amount)} from {districtLabel(dialog.deposit.district)}, slip{' '}
            {dialog.deposit.reference}, has reached the bank.
          </p>
        </FormDialog>
      )}
      {dialog?.kind === 'reject' && (
        <RejectDialog deposit={dialog.deposit} onClose={() => setDialog(null)} onDone={done} />
      )}
    </main>
  );
}

function districtColumn<T extends { id: string; district: SriLankanDistrict }>() {
  return {
    key: 'district' as const,
    label: 'District',
    render: (row: T) => districtLabel(row.district)
  };
}

interface DialogProps {
  onClose: () => void;
  onDone: (message: string) => void;
}

/** A small form in a modal: one submit at a time, the error stays next to the fields. */
function FormDialog({
  title,
  submitLabel,
  onClose,
  onSubmit,
  disabled = false,
  children
}: {
  title: string;
  submitLabel: string;
  onClose: () => void;
  onSubmit: () => Promise<void>;
  disabled?: boolean;
  children: ReactNode;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const close = () => {
    if (!busy) onClose();
  };
  return (
    <Modal open onClose={close} title={title}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (busy || disabled) return;
          setBusy(true);
          setError('');
          onSubmit().catch((err: unknown) => {
            setError(apiErrorMessage(err, 'This could not be saved.'));
            setBusy(false);
          });
        }}
      >
        {children}
        {error && (
          <p role="alert" className="text-body text-status-danger-fg">
            {error}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" disabled={busy} onClick={close}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" loading={busy} disabled={disabled}>
            {submitLabel}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function CountDialog({
  handover,
  onClose,
  onDone
}: DialogProps & { handover: CashHandoverResponse }) {
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const counted = parseAmount(amount);
  const difference = counted === null ? 0 : counted - handover.expectedAmount;
  const differs = counted !== null && Math.abs(difference) >= 0.005;
  return (
    <FormDialog
      title={`Count cash from ${handover.courierName ?? 'the courier'}`}
      submitLabel="Save count"
      disabled={counted === null || (differs && !note.trim())}
      onClose={onClose}
      onSubmit={async () => {
        await cashApi.count(handover.handoverId, {
          countedAmount: counted ?? 0,
          note: differs ? note.trim() : undefined
        });
        onDone('Count saved');
      }}
    >
      <p className="text-body text-primary">Expected {lkr(handover.expectedAmount)}</p>
      <Field
        label="Amount counted (LKR)"
        inputMode="decimal"
        autoFocus
        required
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={amount && counted === null ? 'Enter an amount, at most 2 decimals' : undefined}
      />
      {differs && (
        <>
          <p className="text-body-strong text-status-warning-fg">
            {difference < 0 ? 'Short by' : 'Over by'} {lkr(Math.abs(difference))}
          </p>
          <Field
            label="Why it differs"
            required
            maxLength={256}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </>
      )}
    </FormDialog>
  );
}

function ParcelsDialog({
  handover,
  onClose
}: {
  handover: CashHandoverResponse;
  onClose: () => void;
}) {
  const [entries, setEntries] = useState<CashEntryResponse[] | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let current = true;
    cashApi
      .handoverEntries(handover.handoverId)
      .then((loaded) => {
        if (current) setEntries(loaded);
      })
      .catch((err: unknown) => {
        if (current) setError(apiErrorMessage(err, 'The parcels could not be loaded.'));
      });
    return () => {
      current = false;
    };
  }, [handover.handoverId]);
  return (
    <Modal open onClose={onClose} title={`Parcels of ${handover.courierName ?? 'the courier'}`}>
      {error ? (
        <p role="alert" className="text-body text-status-danger-fg">
          {error}
        </p>
      ) : entries ? (
        <EntryList entries={entries} />
      ) : (
        <p role="status">Loading parcels…</p>
      )}
    </Modal>
  );
}

function EntryList({ entries }: { entries: CashEntryResponse[] }) {
  return (
    <ul className="divide-y divide-border-subtle rounded-lg border border-border-subtle bg-card">
      {entries.map((entry) => (
        <li key={entry.entryId} className="flex items-center justify-between gap-3 px-3 py-2">
          <div className="min-w-0">
            <p className="font-mono text-body-strong tabular-nums text-primary">
              {entry.trackingNumber}
            </p>
            <p className="break-words text-caption text-secondary">
              {entry.note ?? kindLabel(entry)} · {cashTime(entry.recordedAt)}
            </p>
          </div>
          <MoneyAmount amount={entry.amount} currency={entry.currency} size="sm" />
        </li>
      ))}
    </ul>
  );
}

function DepositDialog({ amount, onClose, onDone }: DialogProps & { amount: number }) {
  const [reference, setReference] = useState('');
  return (
    <FormDialog
      title="Deposit the hub's cash"
      submitLabel="Save deposit"
      disabled={!reference.trim()}
      onClose={onClose}
      onSubmit={async () => {
        await cashApi.deposit({ amount, reference: reference.trim() });
        onDone('Deposit saved. An admin confirms it.');
      }}
    >
      <p className="text-body text-primary">You banked {lkr(amount)}.</p>
      <Field
        label="Bank slip number"
        autoFocus
        required
        maxLength={64}
        value={reference}
        onChange={(e) => setReference(e.target.value)}
      />
    </FormDialog>
  );
}

function RefundDialog({ onClose, onDone }: DialogProps) {
  const [trackingNumber, setTrackingNumber] = useState('');
  const [reason, setReason] = useState('');
  return (
    <FormDialog
      title="Refund a delivery fee"
      submitLabel="Refund"
      disabled={!trackingNumber.trim() || !reason.trim()}
      onClose={onClose}
      onSubmit={async () => {
        const entry = await cashApi.refund(trackingNumber.trim(), reason.trim());
        onDone(`${lkr(entry.amount)} refunded. Give it to the sender.`);
      }}
    >
      <p className="text-body text-primary">Only for a parcel SwiftDrop lost or damaged.</p>
      <Field
        label="Tracking number"
        autoFocus
        required
        maxLength={32}
        value={trackingNumber}
        onChange={(e) => setTrackingNumber(e.target.value)}
      />
      <Field
        label="What happened"
        required
        maxLength={256}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
    </FormDialog>
  );
}

function RejectDialog({
  deposit,
  onClose,
  onDone
}: DialogProps & { deposit: CashDepositResponse }) {
  const [reason, setReason] = useState('');
  return (
    <FormDialog
      title="Reject deposit"
      submitLabel="Reject"
      disabled={!reason.trim()}
      onClose={onClose}
      onSubmit={async () => {
        await cashApi.rejectDeposit(deposit.depositId, reason.trim());
        onDone('Deposit rejected. The cash is back at the hub.');
      }}
    >
      <p className="text-body text-primary">
        {lkr(deposit.amount)}, slip {deposit.reference}
      </p>
      <Field
        label="Reason"
        autoFocus
        required
        maxLength={256}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
    </FormDialog>
  );
}
