import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Pencil, RotateCcw } from 'lucide-react';
import { Button, StatusBanner, SurfaceCard } from '@/design-system';
import {
  dispatchApi,
  type DayOfWeek,
  type WorkdayResponse,
  type WorkingHoursRequest
} from '@/services/api/dispatchApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { hhmm } from '@/utils/colomboDate';
import { cn } from '@/utils/cn';

export const WORKDAY = ['dispatch', 'workday'] as const;

const WEEK: { day: DayOfWeek; short: string; long: string }[] = [
  { day: 'MONDAY', short: 'Mon', long: 'Monday' },
  { day: 'TUESDAY', short: 'Tue', long: 'Tuesday' },
  { day: 'WEDNESDAY', short: 'Wed', long: 'Wednesday' },
  { day: 'THURSDAY', short: 'Thu', long: 'Thursday' },
  { day: 'FRIDAY', short: 'Fri', long: 'Friday' },
  { day: 'SATURDAY', short: 'Sat', long: 'Saturday' },
  { day: 'SUNDAY', short: 'Sun', long: 'Sunday' }
];

type TimeKey =
  | 'opensAt'
  | 'closesAt'
  | 'shiftFrom'
  | 'lateAfter'
  | 'breakFrom'
  | 'breakUntil'
  | 'pickupCutoff'
  | 'sameDayCutoff';

const TIMES: { key: TimeKey; label: string; hint: string }[] = [
  { key: 'opensAt', label: 'Opens', hint: 'Working day starts' },
  { key: 'closesAt', label: 'Closes', hint: 'By 20:00 at the latest' },
  { key: 'shiftFrom', label: 'Check-in from', hint: 'From 7:00, up to opening' },
  { key: 'lateAfter', label: 'Late after', hint: 'A later check-in shows as late' },
  { key: 'breakFrom', label: 'Lunch from', hint: 'Lunch can start from' },
  { key: 'breakUntil', label: 'Lunch until', hint: 'Lunch can start until' },
  { key: 'pickupCutoff', label: 'Pickups until', hint: 'No new pickups after this' },
  { key: 'sameDayCutoff', label: 'Same day until', hint: 'Same day bookings before this' }
];

/** "Monday to Saturday", or the days listed when they are not one run. */
export function workingDaysLabel(days: readonly string[]): string {
  const picked = WEEK.filter(({ day }) => days.includes(day));
  if (picked.length === 0) return 'No working days';
  if (picked.length === 7) return 'Every day';
  const first = WEEK.indexOf(picked[0]);
  const run = picked.every((entry, i) => WEEK.indexOf(entry) === first + i);
  if (run && picked.length > 2) return `${picked[0].long} to ${picked[picked.length - 1].long}`;
  return picked.map((entry) => entry.long).join(', ');
}

function toForm(hours: WorkdayResponse): WorkingHoursRequest {
  return {
    workingDays: [...hours.workingDays],
    opensAt: hhmm(hours.opensAt),
    closesAt: hhmm(hours.closesAt),
    shiftFrom: hhmm(hours.shiftFrom),
    lateAfter: hhmm(hours.lateAfter),
    breakFrom: hhmm(hours.breakFrom),
    breakUntil: hhmm(hours.breakUntil),
    breakMinutes: hours.breakMinutes,
    pickupCutoff: hhmm(hours.pickupCutoff),
    sameDayCutoff: hhmm(hours.sameDayCutoff)
  };
}

const inputClass =
  'h-[34px] w-full rounded-md border border-border-subtle bg-card px-2 text-sm tabular-nums text-primary focus-visible:outline-border-focus';

/**
 * System admin: the working days and hours couriers work to, with the lunch window and the cut-offs.
 * The standard plan is Monday to Saturday, 8:00 to 17:00; changes apply at once.
 */
export function WorkingHoursCard({ hours }: { hours: WorkdayResponse }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState<WorkingHoursRequest | null>(null);
  const [notice, setNotice] = useState('');

  const done = (today: WorkdayResponse, message: string) => {
    queryClient.setQueryData(WORKDAY, today);
    setForm(null);
    setNotice(message);
  };
  const save = useMutation({
    mutationFn: dispatchApi.updateWorkingHours,
    onSuccess: (today) => done(today, 'Working hours saved. They apply from now on.')
  });
  const reset = useMutation({
    mutationFn: dispatchApi.resetWorkingHours,
    onSuccess: (today) => done(today, 'The standard working hours are back.')
  });
  const error = save.error ?? reset.error;
  const busy = save.isPending || reset.isPending;

  const startEditing = () => {
    save.reset();
    reset.reset();
    setNotice('');
    setForm(toForm(hours));
  };
  const toggleDay = (day: DayOfWeek) =>
    setForm((current) =>
      current
        ? {
            ...current,
            workingDays: current.workingDays.includes(day)
              ? current.workingDays.filter((d) => d !== day)
              : [...current.workingDays, day]
          }
        : current
    );
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (form && form.workingDays.length > 0) save.mutate(form);
  };

  return (
    <SurfaceCard className="space-y-4 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-h3 text-primary">Working hours</h2>
          <p className="text-body text-secondary">
            {workingDaysLabel(hours.workingDays)}, {hhmm(hours.opensAt)} to {hhmm(hours.closesAt)}.
            Other days are closed.
          </p>
        </div>
        {form ? null : (
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              density="compact"
              leftIcon={<RotateCcw />}
              disabled={busy}
              loading={reset.isPending}
              onClick={() => reset.mutate()}
            >
              Restore standard
            </Button>
            <Button density="compact" leftIcon={<Pencil />} onClick={startEditing}>
              Edit hours
            </Button>
          </div>
        )}
      </div>

      {notice ? (
        <StatusBanner tone="success" title={notice} onDismiss={() => setNotice('')} />
      ) : null}
      {error ? (
        <StatusBanner tone="danger" title="Not saved">
          {apiErrorMessage(error, 'Check the hours and try again.')}
        </StatusBanner>
      ) : null}

      {form ? (
        <form onSubmit={submit} className="space-y-4" aria-label="Edit working hours">
          <fieldset>
            <legend className="mb-2 text-caption font-medium text-secondary">Working days</legend>
            <div className="flex flex-wrap gap-2">
              {WEEK.map(({ day, short, long }) => {
                const on = form.workingDays.includes(day);
                return (
                  <button
                    key={day}
                    type="button"
                    aria-pressed={on}
                    aria-label={long}
                    onClick={() => toggleDay(day)}
                    className={cn(
                      'h-9 min-w-12 rounded-md border px-3 text-sm font-semibold transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
                      on
                        ? 'border-border-selected bg-brand-subtle text-brand-primary'
                        : 'border-border-subtle bg-card text-secondary hover:bg-canvas'
                    )}
                  >
                    {short}
                  </button>
                );
              })}
            </div>
            {form.workingDays.length === 0 ? (
              <p className="mt-1 text-caption text-status-danger">Choose at least one day.</p>
            ) : null}
          </fieldset>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {TIMES.map(({ key, label, hint }) => (
              <label
                key={key}
                className="flex flex-col gap-1 text-caption font-medium text-secondary"
              >
                {label}
                <input
                  type="time"
                  required
                  step={300}
                  value={form[key]}
                  onChange={(event) => setForm({ ...form, [key]: event.target.value })}
                  className={inputClass}
                />
                <span className="font-normal text-muted">{hint}</span>
              </label>
            ))}
            <label className="flex flex-col gap-1 text-caption font-medium text-secondary">
              Lunch minutes
              <input
                type="number"
                required
                min={10}
                max={120}
                step={5}
                value={form.breakMinutes}
                onChange={(event) => setForm({ ...form, breakMinutes: Number(event.target.value) })}
                className={inputClass}
              />
              <span className="font-normal text-muted">10 to 120</span>
            </label>
          </div>

          <div className="flex flex-wrap justify-end gap-2">
            <Button
              variant="outline"
              density="compact"
              disabled={busy}
              onClick={() => setForm(null)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              density="compact"
              loading={save.isPending}
              disabled={busy || form.workingDays.length === 0}
            >
              Save hours
            </Button>
          </div>
        </form>
      ) : (
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-4">
          {[
            ['Check-in from', hhmm(hours.shiftFrom)],
            ['Late after', hhmm(hours.lateAfter)],
            [
              'Lunch',
              `${hours.breakMinutes} min, from ${hhmm(hours.breakFrom)} to ${hhmm(hours.breakUntil)}`
            ],
            ['Pickups until', hhmm(hours.pickupCutoff)],
            ['Same day until', hhmm(hours.sameDayCutoff)]
          ].map(([label, value]) => (
            <div key={label}>
              <dt className="text-caption text-secondary">{label}</dt>
              <dd className="text-body tabular-nums text-primary">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </SurfaceCard>
  );
}
