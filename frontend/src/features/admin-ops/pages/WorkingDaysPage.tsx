import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { Button, Field, StatusBanner, SurfaceCard } from '@/design-system';
import { dispatchApi, type WorkdayResponse } from '@/services/api/dispatchApi';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { colomboToday, dayLabel } from '@/utils/colomboDate';
import { useAuthStore } from '@/store/useAuthStore';
import { WORKDAY, WorkingHoursCard } from '../components/WorkingHoursCard';

/**
 * System admin: the working days and hours couriers work to, and the holidays (Poya days, public
 * holidays) on which nobody works.
 */
export function WorkingDaysPage() {
  const isAdmin = useAuthStore((s) => s.user?.role) === 'SYSTEM_ADMIN';
  const queryClient = useQueryClient();
  const workday = useQuery({ queryKey: WORKDAY, queryFn: dispatchApi.getWorkday, enabled: isAdmin });
  const [day, setDay] = useState('');
  const [name, setName] = useState('');

  const saved = (closedDays: WorkdayResponse['closedDays']) =>
    queryClient.setQueryData<WorkdayResponse>(WORKDAY, (old) => (old ? { ...old, closedDays } : old));
  const add = useMutation({
    mutationFn: () => dispatchApi.addClosedDay({ day, name: name.trim() }),
    onSuccess: (closedDays) => {
      saved(closedDays);
      setDay('');
      setName('');
    }
  });
  const remove = useMutation({ mutationFn: dispatchApi.removeClosedDay, onSuccess: saved });

  if (!isAdmin) {
    return (
      <main className="p-4 md:p-6">
        <StatusBanner tone="warning" title="System admins only" />
      </main>
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (day && name.trim()) add.mutate();
  };
  const hours = workday.data;
  const error = add.error ?? remove.error;

  return (
    <main className="mx-auto max-w-3xl space-y-5 p-4 md:p-6" data-density="compact">
      <header>
        <h1 className="text-2xl font-bold tracking-tight text-primary">Working days</h1>
      </header>

      {workday.isError ? (
        <StatusBanner tone="danger" title="The working day could not be loaded">
          {apiErrorMessage(workday.error, 'Refresh to try again.')}
        </StatusBanner>
      ) : null}
      {hours ? <WorkingHoursCard hours={hours} /> : null}

        {error ? (
        <StatusBanner tone="danger" title="Holiday not saved">
          {apiErrorMessage(error, 'Try again.')}
        </StatusBanner>
      ) : null}

      <h2 className="text-h3 text-primary">Holidays</h2>
      <SurfaceCard className="p-4">
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-caption font-medium text-secondary">
            Day
            <input
              type="date"
              required
              min={colomboToday()}
              value={day}
              onChange={(event) => setDay(event.target.value)}
              className="h-[34px] rounded-md border border-border-subtle bg-card px-2 text-sm text-primary focus-visible:outline-border-focus"
            />
          </label>
          <div className="min-w-48 flex-1">
            <Field
              label="Holiday"
              value={name}
              maxLength={64}
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <Button type="submit" density="compact" disabled={!day || !name.trim()} loading={add.isPending}>
            Close this day
          </Button>
        </form>
      </SurfaceCard>

      <section aria-label="Closed days" className="space-y-2">
        <h2 className="text-overline uppercase text-secondary">Coming holidays</h2>
        {workday.isLoading ? null : !hours?.closedDays.length ? (
          <p className="text-body text-secondary">None listed.</p>
        ) : (
          <ul className="divide-y divide-border-subtle rounded-lg border border-border-subtle bg-card">
            {hours.closedDays.map((closed) => (
              <li key={closed.day} className="flex items-center justify-between gap-3 px-4 py-2">
                <span>
                  <span className="block font-semibold text-primary">{closed.name}</span>
                  <span className="text-caption tabular-nums text-secondary">{dayLabel(closed.day)}</span>
                </span>
                <Button
                  variant="outline"
                  density="compact"
                  aria-label={`Reopen ${closed.name}`}
                  disabled={remove.isPending}
                  onClick={() => remove.mutate(closed.day)}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
