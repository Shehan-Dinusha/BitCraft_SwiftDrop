import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Link } from 'react-router-dom';
import { Button, StatusBanner, SurfaceCard } from '@/design-system';
import { lkr } from '@/features/cash/cashDisplay';
import { districtLabel } from '@/features/profile/profileFormat';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { cashApi, type SriLankanDistrict } from '@/services/api/cashApi';
import { useAuthStore } from '@/store/useAuthStore';

const dateLabel = (date: string) =>
  new Date(`${date}T12:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });

const shortAmount = new Intl.NumberFormat('en-US', {
  notation: 'compact',
  maximumFractionDigits: 1
});

const tooltipStyle = {
  border: '1px solid var(--color-border-subtle)',
  borderRadius: 12,
  background: 'var(--color-surface-card)',
  color: 'var(--color-text-primary)',
  fontSize: 13
};

export interface RevenueReportProps {
  from: string;
  to: string;
  district?: SriLankanDistrict;
  /** An admin compares districts; a dispatcher only has their own. */
  showDistricts: boolean;
}

/** Revenue and cash for the reporting period, from the cash ledger. */
export function RevenueReport({ from, to, district, showDistricts }: RevenueReportProps) {
  const userId = useAuthStore((state) => state.user?.id);
  const report = useQuery({
    queryKey: ['cash-report', userId, district ?? 'all', from, to],
    queryFn: () => cashApi.report(from, to, district),
    refetchInterval: 60_000,
    retry: false
  });
  const { data } = report;
  const notBanked = data ? data.withCouriers + data.toCount + data.atHub : 0;

  return (
    <section aria-labelledby="revenue-heading" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="revenue-heading" className="text-h2 text-primary">
          Revenue and cash
        </h2>
        <Link to="/ops/cash" className="text-label text-brand-primary hover:underline">
          Open Cash
        </Link>
      </div>

      {report.isError && (
        <StatusBanner
          tone="danger"
          title="Revenue could not be loaded"
          action={
            <Button variant="outline" onClick={() => void report.refetch()}>
              Retry
            </Button>
          }
        >
          {apiErrorMessage(report.error, 'Check your connection and retry.')}
        </StatusBanner>
      )}
      {report.isPending && <p role="status">Loading revenue…</p>}

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <Tile
              label="Revenue"
              amount={data.revenue}
              detail={
                data.refunds > 0
                  ? `${data.parcels} parcels, less ${lkr(data.refunds)} refunded`
                  : `${data.parcels} parcels`
              }
            />
            <Tile
              label="Not banked yet"
              amount={notBanked}
              detail={`Couriers ${lkr(data.withCouriers)} · hubs ${lkr(data.toCount + data.atHub)}`}
            />
            <Tile
              label="Shortages"
              amount={data.shortage}
              detail={data.excess > 0 ? `${lkr(data.excess)} over` : 'Missing when counted'}
            />
          </div>

          <SurfaceCard density="compact" className="rounded-xl shadow-sm">
            <h3 className="p-2 pb-4 text-base font-semibold">Revenue per day (LKR)</h3>
            {data.days.every((day) => day.revenue === 0) ? (
              <p className="px-2 py-10 text-center text-body text-secondary">
                No revenue in this period
              </p>
            ) : (
              <div className="h-72 min-w-0 text-xs" role="img" aria-label="Revenue per day">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart
                    data={data.days}
                    accessibilityLayer
                    margin={{ right: 16, top: 12, left: 0, bottom: 8 }}
                  >
                    <CartesianGrid vertical={false} stroke="var(--color-border-subtle)" />
                    <XAxis
                      dataKey="date"
                      tickFormatter={dateLabel}
                      axisLine={false}
                      tickLine={false}
                      tickMargin={12}
                      minTickGap={30}
                      stroke="var(--color-text-secondary)"
                    />
                    <YAxis
                      tickFormatter={(value) => shortAmount.format(Number(value))}
                      axisLine={false}
                      tickLine={false}
                      tickMargin={8}
                      stroke="var(--color-text-secondary)"
                    />
                    <Tooltip
                      contentStyle={tooltipStyle}
                      cursor={{ fill: 'var(--color-surface-canvas)' }}
                      labelFormatter={(value) => dateLabel(String(value))}
                      formatter={(value) => [lkr(Number(value)), 'Revenue']}
                    />
                    <Bar
                      dataKey="revenue"
                      name="Revenue"
                      fill="var(--color-brand-primary)"
                      radius={[4, 4, 0, 0]}
                      maxBarSize={28}
                      isAnimationActive={false}
                    />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
            <details className="mx-2 mt-3 border-t border-border-subtle pt-3 text-xs text-secondary">
              <summary className="w-fit cursor-pointer font-medium">View as a table</summary>
              <Table
                caption="Revenue per day"
                head={['Date', 'Parcels', 'Revenue']}
                rows={data.days.map((day) => [
                  dateLabel(day.date),
                  day.parcels.toLocaleString(),
                  lkr(day.revenue)
                ])}
              />
            </details>
          </SurfaceCard>

          {showDistricts && data.districts.length > 0 && (
            <SurfaceCard density="compact" className="rounded-xl shadow-sm">
              <h3 className="p-2 text-base font-semibold">By district</h3>
              <Table
                caption="Revenue and last-mile deliveries by district"
                head={['District', 'Parcels', 'Revenue', 'Delivered for other districts']}
                rows={data.districts.map((row) => [
                  districtLabel(row.district),
                  row.parcels.toLocaleString(),
                  lkr(row.revenue),
                  row.lastMileDelivered.toLocaleString()
                ])}
              />
            </SurfaceCard>
          )}
        </>
      )}
    </section>
  );
}

function Tile({ label, amount, detail }: { label: string; amount: number; detail: string }) {
  return (
    <SurfaceCard density="compact" className="rounded-xl shadow-sm">
      <div className="p-2">
        <p className="text-sm font-medium text-secondary">{label}</p>
        <p className="mt-3 text-2xl font-semibold tracking-tight tabular-nums">{lkr(amount)}</p>
        <p className="mt-2 text-xs text-secondary">{detail}</p>
      </div>
    </SurfaceCard>
  );
}

function Table({ caption, head, rows }: { caption: string; head: string[]; rows: string[][] }) {
  return (
    <div className="overflow-x-auto">
      <table className="mt-3 w-full text-right text-sm tabular-nums">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr>
            {head.map((label, index) => (
              <th
                key={label}
                scope="col"
                className={`p-2 font-medium text-secondary ${index === 0 ? 'text-left' : ''}`}
              >
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map(([first, ...rest]) => (
            <tr key={first} className="border-t border-border-subtle text-primary">
              <th scope="row" className="p-2 text-left font-normal">
                {first}
              </th>
              {rest.map((value, index) => (
                <td key={head[index + 1]} className="whitespace-nowrap p-2">
                  {value}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
