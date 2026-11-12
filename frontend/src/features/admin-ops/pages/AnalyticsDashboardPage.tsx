import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import { Button, Field, StatusBanner, SurfaceCard } from '@/design-system';
import { analyticsAiApi } from '@/services/api/analyticsAiApi';
import { useAuthStore } from '@/store/useAuthStore';

const series = [
  { key: 'created', label: 'Created', color: 'var(--color-fsm-created-fg)' },
  { key: 'delivered', label: 'Delivered', color: 'var(--color-fsm-delivered-fg)' },
  { key: 'failed', label: 'Failed', color: 'var(--color-fsm-failed-fg)' }
] as const;

function initialRange() {
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Colombo',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date());
  const start = new Date(`${today}T00:00:00Z`);
  start.setUTCDate(start.getUTCDate() - 6);
  return { from: start.toISOString().slice(0, 10), to: today };
}

export function AnalyticsDashboardPage() {
  const [range, setRange] = useState(initialRange);
  const [draft, setDraft] = useState(range);
  const userId = useAuthStore((state) => state.user?.id);
  const dayCount = (Date.parse(draft.to) - Date.parse(draft.from)) / 86_400_000 + 1;
  const valid = Boolean(draft.from && draft.to && dayCount >= 1 && dayCount <= 90);
  const reports = useQuery({
    queryKey: ['analytics-reports', userId, range.from, range.to],
    queryFn: async ({ signal }) => {
      const [daily, outcomes, workload] = await Promise.all([
        analyticsAiApi.getDailyDeliveries(range.from, range.to, signal),
        analyticsAiApi.getSuccessRate(range.from, range.to, signal),
        analyticsAiApi.getCourierWorkload(signal)
      ]);
      return { daily, outcomes, workload };
    },
    refetchInterval: 15_000,
    retry: false
  });
  const { data } = reports;
  const forbidden = axios.isAxiosError(reports.error) && reports.error.response?.status === 403;
  const outcomes = data
    ? [
        { name: 'Delivered', count: data.outcomes.delivered },
        { name: 'Failed', count: data.outcomes.failed }
      ]
    : [];

  return (
    <main className="mx-auto max-w-7xl space-y-5 p-4 text-primary md:p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Delivery analytics</h1>
          <p className="mt-1 text-sm text-secondary">
            Daily outcomes and current courier workload.
          </p>
        </div>
        <Button
          variant="outline"
          density="compact"
          disabled={reports.isFetching}
          onClick={() => void reports.refetch()}
        >
          {reports.isFetching ? 'Refreshing…' : 'Refresh reports'}
        </Button>
      </header>
      <SurfaceCard density="compact" title="Reporting period">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(event) => {
            event.preventDefault();
            if (valid) setRange({ ...draft });
          }}
        >
          <Field label="From" density="compact" className="max-w-52">
            <input
              type="date"
              required
              value={draft.from}
              className="min-h-[34px] rounded-md border border-border-subtle bg-card px-3 text-sm focus-visible:outline-border-focus"
              onChange={(event) => setDraft({ ...draft, from: event.target.value })}
            />
          </Field>
          <Field label="To" density="compact" className="max-w-52">
            <input
              type="date"
              required
              value={draft.to}
              className="min-h-[34px] rounded-md border border-border-subtle bg-card px-3 text-sm focus-visible:outline-border-focus"
              onChange={(event) => setDraft({ ...draft, to: event.target.value })}
            />
          </Field>
          <Button type="submit" density="compact" disabled={!valid}>
            Apply dates
          </Button>
        </form>
        <p className="mt-2 text-xs text-secondary">
          Inclusive Asia/Colombo dates, up to 90 days. Workload always shows the current projection.
        </p>
        {!valid && (
          <p role="alert" className="mt-2 text-sm text-status-danger">
            Choose a date range of 1 to 90 days with From before or equal to To.
          </p>
        )}
      </SurfaceCard>
      {reports.isError && (
        <StatusBanner
          tone="danger"
          title={forbidden ? 'Operational access required' : 'Reports could not be refreshed'}
          action={
            <Button variant="outline" onClick={() => void reports.refetch()}>
              Retry reports
            </Button>
          }
        >
          {forbidden
            ? 'Sign in as a dispatcher or system administrator.'
            : data
              ? 'Showing the last successful report. Try refreshing again.'
              : 'Check your connection and retry.'}
        </StatusBanner>
      )}
      {reports.isPending && <p role="status">Loading delivery reports…</p>}
      {data && (
        <>
          <p className="text-xs text-secondary" role="status">
            Showing {range.from} to {range.to}. Refreshes every 15 seconds while this tab is active.
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            {[
              ['Completed outcomes', data.outcomes.completed.toLocaleString()],
              [
                'Success rate',
                data.outcomes.successRatePercent === null
                  ? 'No completed outcomes'
                  : `${data.outcomes.successRatePercent.toFixed(2)}%`
              ],
              ['Current active orders', data.workload.totalActiveOrders.toLocaleString()]
            ].map(([title, value]) => (
              <SurfaceCard key={title} density="compact" title={title}>
                <p className="font-mono text-xl font-semibold tabular-nums">{value}</p>
              </SurfaceCard>
            ))}
          </div>
          <SurfaceCard density="compact" title="Deliveries per day">
            {!data.daily.days.some((day) => day.created || day.delivered || day.failed) && (
              <p className="mb-3 text-sm text-secondary">No delivery activity in this period.</p>
            )}
            <div className="h-72 min-w-0" aria-label="Daily created, delivered and failed counts">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.daily.days} accessibilityLayer margin={{ right: 16, top: 8 }}>
                  <CartesianGrid stroke="var(--color-border-subtle)" strokeDasharray="3 3" />
                  <XAxis
                    dataKey="date"
                    tickFormatter={(value: string | number) => String(value).slice(5)}
                    stroke="var(--color-text-secondary)"
                  />
                  <YAxis allowDecimals={false} stroke="var(--color-text-secondary)" width={45} />
                  <Tooltip />
                  <Legend />
                  {series.map((item) => (
                    <Line
                      key={item.key}
                      dataKey={item.key}
                      name={item.label}
                      stroke={item.color}
                      strokeWidth={2}
                      dot={false}
                      isAnimationActive={false}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </div>
            <details className="mt-3 text-sm">
              <summary className="cursor-pointer">View daily counts as a table</summary>
              <div className="overflow-x-auto">
                <table className="mt-2 w-full text-right font-mono tabular-nums">
                  <caption className="sr-only">
                    Daily delivery counts for the selected reporting period
                  </caption>
                  <thead>
                    <tr>
                      {['Date', 'Created', 'Delivered', 'Failed'].map((label) => (
                        <th key={label} scope="col" className="p-2">
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.daily.days.map((day) => (
                      <tr key={day.date}>
                        <th scope="row" className="p-2 font-normal">
                          {day.date}
                        </th>
                        <td className="p-2">{day.created}</td>
                        <td className="p-2">{day.delivered}</td>
                        <td className="p-2">{day.failed}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </details>
          </SurfaceCard>
          <div className="grid gap-4 lg:grid-cols-2">
            <SurfaceCard density="compact" title="Success vs failure">
              <p className="mb-3 text-xs text-secondary">
                Delivered {data.outcomes.delivered} · Failed {data.outcomes.failed}. Cancellations
                and returns excluded.
              </p>
              {data.outcomes.completed === 0 ? (
                <p className="py-12 text-center text-secondary">
                  No completed outcomes in this period.
                </p>
              ) : (
                <div className="h-64">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={outcomes} accessibilityLayer>
                      <XAxis dataKey="name" />
                      <YAxis allowDecimals={false} width={45} />
                      <Tooltip />
                      <Bar
                        dataKey="count"
                        name="Outcomes"
                        fill="var(--color-brand-primary)"
                        isAnimationActive={false}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </SurfaceCard>
            <SurfaceCard density="compact" title="Courier workload now">
              <p className="mb-3 text-xs text-secondary">
                Active assigned orders, including known idle couriers. Not online availability. Read
                at {new Date(data.workload.asOf).toLocaleTimeString()}.
              </p>
              {data.workload.couriers.length === 0 ? (
                <p className="py-12 text-center text-secondary">
                  No couriers in the projection yet.
                </p>
              ) : (
                <>
                  <div className="max-h-96 overflow-y-auto">
                    <div style={{ height: Math.max(240, data.workload.couriers.length * 36) }}>
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart
                          data={data.workload.couriers}
                          layout="vertical"
                          accessibilityLayer
                          margin={{ right: 16 }}
                        >
                          <XAxis type="number" allowDecimals={false} />
                          <YAxis
                            type="category"
                            dataKey="courierId"
                            width={95}
                            tickFormatter={(value: string | number) => String(value).slice(0, 8)}
                          />
                          <Tooltip />
                          <Bar
                            dataKey="activeOrders"
                            name="Active orders"
                            fill="var(--color-brand-primary)"
                            isAnimationActive={false}
                          />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                  <details className="mt-3 text-sm">
                    <summary className="cursor-pointer">View courier IDs and counts</summary>
                    <ul className="mt-2 space-y-1 font-mono text-xs tabular-nums">
                      {data.workload.couriers.map((courier) => (
                        <li key={courier.courierId}>
                          {courier.courierId}: {courier.activeOrders} active orders
                        </li>
                      ))}
                    </ul>
                  </details>
                </>
              )}
            </SurfaceCard>
          </div>
        </>
      )}
    </main>
  );
}

export default AnalyticsDashboardPage;
