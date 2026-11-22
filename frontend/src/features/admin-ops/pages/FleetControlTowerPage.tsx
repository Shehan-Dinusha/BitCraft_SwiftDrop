import { useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Inbox, PackagePlus, RefreshCw, Send, Truck } from 'lucide-react';
import { Button, SelectDropdown, StatusBanner, SurfaceCard } from '@/design-system';
import { analyticsAiApi } from '@/services/api/analyticsAiApi';
import type { SriLankanDistrict } from '@/services/api/dispatchApi';
import { useAuthStore } from '@/store/useAuthStore';
import { districtLabel } from '@/features/profile/profileFormat';
import { colomboToday } from '@/utils/colomboDate';
import { vehicleLabel } from '@/utils/vehicles';
import { LiveFleetHeatmap } from '../components/LiveFleetHeatmap';
import { SHIFT } from '../components/dispatch/CouriersTab';
import { Badge, CELL, DispatchTable, TableSkeleton } from '../components/dispatch/DispatchTable';
import { ALL_DISTRICTS, DISTRICT_OPTIONS } from '../components/dispatch/districtOptions';
import { sortCouriers } from '../components/dispatch/dispatchQueues';
import { useDispatchData } from '../components/dispatch/useDispatchData';

const TODAY_REFRESH_MS = 60_000;

/** One job waiting in the dispatch console, as a link to its tab. */
function JobCard({
  label,
  count,
  icon,
  to
}: {
  label: string;
  count: number | undefined;
  icon: ReactNode;
  to: string;
}) {
  return (
    <Link
      to={to}
      className="group flex items-center gap-4 rounded-xl border border-border-strong bg-card p-4 shadow-raised transition-colors hover:border-border-selected focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
    >
      <span
        aria-hidden="true"
        className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary [&_svg]:size-5"
      >
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-caption text-secondary">{label}</span>
        <span className="block text-h2 tabular-nums">{count ?? '—'}</span>
      </span>
      <ArrowRight
        aria-hidden="true"
        className="size-4 shrink-0 text-muted group-hover:text-brand-primary"
      />
    </Link>
  );
}

/**
 * What needs doing now (each card opens that job in Dispatch), how today is going, and who is on shift.
 * A dispatcher sees their own district (the server decides); a system admin sees all of them or picks one.
 */
export function FleetControlTowerPage() {
  const user = useAuthStore((state) => state.user);
  const isAdmin = user?.role === 'SYSTEM_ADMIN';
  const [adminDistrict, setAdminDistrict] = useState<string>(ALL_DISTRICTS);
  const scope =
    isAdmin && adminDistrict !== ALL_DISTRICTS ? (adminDistrict as SriLankanDistrict) : undefined;
  const oneDistrict = !isAdmin || scope !== undefined;

  const { unassigned, assigned, ready, incoming, couriers, refresh } = useDispatchData(
    user?.id,
    scope,
    oneDistrict
  );
  const date = colomboToday();
  const today = useQuery({
    queryKey: ['analytics-today', user?.id, scope ?? 'all', date],
    queryFn: async ({ signal }) =>
      (await analyticsAiApi.getDailyDeliveries(date, date, scope, signal)).days[0],
    refetchInterval: TODAY_REFRESH_MS,
    retry: 1
  });

  const lists = [unassigned, assigned, ready, incoming, couriers];
  const failed = lists.some((list) => list.isError);
  const fetching = lists.some((list) => list.isFetching) || today.isFetching;
  const onShift = sortCouriers(
    (couriers.data ?? []).filter((c) => c.shiftStatus && c.shiftStatus !== 'OFFLINE')
  );
  const tab = (id: string) => `/ops/dispatch?tab=${id}${scope ? `&district=${scope}` : ''}`;

  return (
    <main className="mx-auto max-w-[1400px] space-y-5 p-4 md:p-6" data-density="compact">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-h1 text-primary">Control tower</h1>
          {!isAdmin && user?.district ? (
            <p className="text-caption text-secondary">{districtLabel(user.district)} district</p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-end gap-2">
          {isAdmin ? (
            <SelectDropdown
              label="District"
              searchable
              options={DISTRICT_OPTIONS}
              value={adminDistrict}
              onChange={setAdminDistrict}
              className="w-56"
            />
          ) : null}
          <Button
            variant="outline"
            density="compact"
            leftIcon={<RefreshCw className={fetching ? 'animate-spin' : undefined} />}
            onClick={() => {
              void refresh();
              void today.refetch();
            }}
          >
            Refresh
          </Button>
        </div>
      </header>

      {failed ? (
        <StatusBanner tone="warning" title="Some numbers could not be refreshed">
          Retrying automatically. Anything shown may be out of date.
        </StatusBanner>
      ) : null}

      <section aria-label="To do now" className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <JobCard
          label="To assign"
          count={unassigned.data?.length}
          icon={<PackagePlus />}
          to={tab('assign')}
        />
        <JobCard
          label="With couriers"
          count={assigned.data?.total}
          icon={<Truck />}
          to={tab('assigned')}
        />
        {oneDistrict ? (
          <>
            <JobCard
              label="Ready to send"
              count={ready.data?.length}
              icon={<Send />}
              to={tab('ready')}
            />
            <JobCard
              label="Incoming"
              count={incoming.data?.length}
              icon={<Inbox />}
              to={tab('incoming')}
            />
          </>
        ) : null}
      </section>

      <SurfaceCard title="Today">
        {today.isError ? (
          <p className="text-body text-secondary">Today&apos;s numbers could not be loaded.</p>
        ) : (
          <dl className="grid grid-cols-3 gap-4">
            {[
              ['Booked', today.data?.created],
              ['Delivered', today.data?.delivered],
              ['Failed', today.data?.failed]
            ].map(([label, value]) => (
              <div key={label}>
                <dt className="text-caption text-secondary">{label}</dt>
                <dd className="text-h2 tabular-nums text-primary">{value ?? '—'}</dd>
              </div>
            ))}
          </dl>
        )}
      </SurfaceCard>

      {isAdmin && !scope ? (
        <LiveFleetHeatmap couriers={couriers.data} />
      ) : (
        <SurfaceCard title={`Couriers on shift (${onShift.length})`}>
          {couriers.data === undefined ? (
            couriers.isError ? null : (
              <TableSkeleton />
            )
          ) : onShift.length === 0 ? (
            <p className="py-4 text-center text-body text-secondary">No couriers on shift.</p>
          ) : (
            <DispatchTable
              label="Couriers on shift"
              headers={['Courier', 'Vehicle', 'Shift', 'Parcels']}
            >
              {onShift.map((courier) => {
                const shift = SHIFT[courier.shiftStatus ?? ''];
                return (
                  <tr key={courier.courierId} className="hover:bg-canvas">
                    <td className={`${CELL} font-semibold`}>
                      {courier.courierName ?? courier.courierId}
                    </td>
                    <td className={CELL}>{vehicleLabel(courier.vehicleType)}</td>
                    <td className={CELL}>
                      <Badge tone={shift?.tone ?? 'neutral'}>{shift?.label ?? 'Unknown'}</Badge>
                    </td>
                    <td className={CELL}>
                      {courier.activeParcelsCount ?? 0} of {courier.maxCapacity ?? 0}
                    </td>
                  </tr>
                );
              })}
            </DispatchTable>
          )}
        </SurfaceCard>
      )}
    </main>
  );
}
