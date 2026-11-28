import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';
import {
  Activity,
  ArrowUpRight,
  CalendarDays,
  CheckCheck,
  Package,
  RefreshCw,
  Truck
} from 'lucide-react';
import {
  Line,
  LineChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from 'recharts';
import { Link } from 'react-router-dom';
import { Button, SelectDropdown, StatusBanner, SurfaceCard } from '@/design-system';
import { analyticsAiApi } from '@/services/api/analyticsAiApi';
import type { SriLankanDistrict } from '@/services/api/dispatchApi';
import { districtLabel } from '@/features/profile/profileFormat';
import { useAuthStore } from '@/store/useAuthStore';
import { colomboToday } from '@/utils/colomboDate';
import { ALL_DISTRICTS, DISTRICT_OPTIONS } from '../components/dispatch/districtOptions';
import { RevenueReport } from '../components/RevenueReport';

const series = [
  { key: 'created', label: 'Created', color: 'var(--color-fsm-created-fg)' },
  { key: 'delivered', label: 'Delivered', color: 'var(--color-fsm-delivered-fg)' },
  { key: 'failed', label: 'Failed', color: 'var(--color-fsm-failed-fg)' }
] as const;
const tooltipStyle = {
  border: '1px solid var(--color-border-subtle)',
  borderRadius: 12,
  background: 'var(--color-surface-card)',
  color: 'var(--color-text-primary)',
  boxShadow: '0 8px 24px rgb(15 23 42 / 8%)',
  fontSize: 13
};
const dateLabel = (date: string) =>
  new Date(`${date}T12:00:00`).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
function initialRange(days = 7) {
  const today = colomboToday();
  const start = new Date(`${today}T00:00:00Z`);
  start.setUTCDate(start.getUTCDate() - days + 1);
  return { from: start.toISOString().slice(0, 10), to: today };
}
function Metric({
  label,
  value,
  detail,
  icon
}: {
  label: string;
  value: string;
  detail: string;
  icon: ReactNode;
}) {
  return (
    <SurfaceCard density="compact" className="rounded-xl shadow-sm">
      <div className="p-2">
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm font-medium text-secondary">{label}</p>
          <span className="rounded-lg bg-brand-subtle p-2 text-brand-primary">{icon}</span>
        </div>
        <p className="mt-3 text-3xl font-semibold tracking-tight tabular-nums">{value}</p>
        <p className="mt-2 text-xs text-secondary">{detail}</p>
      </div>
    </SurfaceCard>
  );
}
function EmptyChart({
  icon,
  title,
  children
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-56 flex-col items-center justify-center px-5 py-8 text-center">
      <span className="mb-3 rounded-full bg-canvas p-4 text-muted">{icon}</span>
      <p className="font-semibold text-primary">{title}</p>
      <div className="mt-2 max-w-xs text-sm leading-relaxed text-secondary">{children}</div>
    </div>
  );
}
export function AnalyticsDashboardPage() {
  const [range, setRange] = useState(() => initialRange());
  const [draft, setDraft] = useState(range);
  const user = useAuthStore((state) => state.user);
  const userId = user?.id;
  const isAdmin = user?.role === 'SYSTEM_ADMIN';
  // A dispatcher always gets their own district from the server; an admin sees all or picks one.
  const [adminDistrict, setAdminDistrict] = useState<string>(ALL_DISTRICTS);
  const scope =
    isAdmin && adminDistrict !== ALL_DISTRICTS ? (adminDistrict as SriLankanDistrict) : undefined;
  const dayCount = (Date.parse(draft.to) - Date.parse(draft.from)) / 86_400_000 + 1;
  const valid = Boolean(draft.from && draft.to && dayCount >= 1 && dayCount <= 90);
  const reports = useQuery({
    queryKey: ['analytics-reports', userId, scope ?? 'all', range.from, range.to],
    queryFn: async ({ signal }) => {
      const [daily, outcomes, workload] = await Promise.all([
        analyticsAiApi.getDailyDeliveries(range.from, range.to, scope, signal),
        analyticsAiApi.getSuccessRate(range.from, range.to, scope, signal),
        analyticsAiApi.getCourierWorkload(scope, signal)
      ]);
      return { daily, outcomes, workload };
    },
    refetchInterval: 15_000,
    retry: false
  });
  const { data } = reports;
  const courierProfiles = useQuery({
    queryKey: ['analytics-courier-names', userId, scope ?? 'all'],
    queryFn: () => analyticsAiApi.getCourierPerformances(scope),
    staleTime: 60_000,
    retry: false
  });
  const courierNames = new Map(
    (Array.isArray(courierProfiles.data) ? courierProfiles.data : []).map((courier) => [
      courier.courierId,
      courier.courierName
    ])
  );
  const forbidden = axios.isAxiosError(reports.error) && reports.error.response?.status === 403;
  const created = data?.daily.days.reduce((sum, day) => sum + day.created, 0) ?? 0;
  const outcomes = data
    ? [
        { name: 'Delivered', count: data.outcomes.delivered, color: series[1].color },
        { name: 'Failed', count: data.outcomes.failed, color: series[2].color }
      ]
    : [];
  const workload = [...(data?.workload.couriers ?? [])]
    .map((courier) => ({
      ...courier,
      courierLabel: courierNames.get(courier.courierId) || courier.courierId.slice(0, 8)
    }))
    .sort((a, b) => b.activeOrders - a.activeOrders);
  return (
    <div className="mx-auto max-w-[1440px] space-y-6 p-4 text-primary md:p-7 lg:p-8">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Delivery analytics</h1>
          {!isAdmin && user?.district ? (
            <p className="text-caption text-secondary">{districtLabel(user.district)} district</p>
          ) : null}
        </div>
        <div className="flex flex-wrap items-end gap-3">
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
          {reports.dataUpdatedAt > 0 && (
            <span className="hidden text-xs text-secondary sm:block">
              Updated{' '}
              {new Date(reports.dataUpdatedAt).toLocaleTimeString('en-GB', {
                hour: '2-digit',
                minute: '2-digit'
              })}
            </span>
          )}
          <Button
            variant="outline"
            density="compact"
            disabled={reports.isFetching}
            onClick={() => void reports.refetch()}
            leftIcon={<RefreshCw className={reports.isFetching ? 'animate-spin' : ''} />}
          >
            {reports.isFetching ? 'Refreshing…' : 'Refresh reports'}
          </Button>
        </div>
      </header>
      <SurfaceCard density="compact" className="rounded-xl shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4 p-1">
          <div className="flex items-center gap-3">
            <span className="rounded-lg bg-canvas p-2.5 text-secondary">
              <CalendarDays size={19} />
            </span>
            <div>
              <h2 className="text-sm font-semibold">Reporting period</h2>
              <p className="mt-0.5 text-xs text-secondary">Asia/Colombo · up to 90 days</p>
            </div>
          </div>
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              if (valid) setRange({ ...draft });
            }}
          >
            <div
              className="mr-1 flex gap-1 rounded-lg bg-canvas p-1"
              aria-label="Quick date ranges"
            >
              {[7, 30].map((days) => {
                const preset = initialRange(days);
                const active = range.from === preset.from && range.to === preset.to;
                return (
                  <button
                    key={days}
                    type="button"
                    aria-pressed={active}
                    className={`rounded-md px-3 py-2 text-xs font-semibold transition-colors focus-visible:outline-border-focus ${active ? 'bg-card text-brand-primary shadow-sm' : 'text-secondary hover:text-primary'}`}
                    onClick={() => {
                      setDraft(preset);
                      setRange(preset);
                    }}
                  >
                    Last {days} days
                  </button>
                );
              })}
            </div>
            {(['from', 'to'] as const).map((key) => (
              <label key={key} className="flex flex-col gap-1 text-xs font-medium text-secondary">
                {key === 'from' ? 'From' : 'To'}
                <input
                  type="date"
                  required
                  aria-label={key === 'from' ? 'From' : 'To'}
                  value={draft[key]}
                  onChange={(event) => setDraft({ ...draft, [key]: event.target.value })}
                  className="h-[34px] min-w-0 rounded-md border border-border-subtle bg-card px-2 text-sm text-primary focus-visible:outline-border-focus"
                />
              </label>
            ))}
            <Button type="submit" density="compact" disabled={!valid}>
              Apply dates
            </Button>
          </form>
        </div>
        {!valid && (
          <p role="alert" className="mt-3 text-sm text-status-danger">
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
      {reports.isPending && (
        <div role="status" className="grid animate-pulse gap-4 sm:grid-cols-4">
          {[1, 2, 3, 4].map((key) => (
            <div key={key} className="h-36 rounded-xl border border-border-subtle bg-card" />
          ))}
          <span className="sr-only">Loading delivery reports…</span>
        </div>
      )}
      {data && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-secondary">
            <p role="status">
              Showing {range.from} to {range.to}
            </p>
            <span className="inline-flex items-center gap-1.5">
              <Activity size={13} /> Auto-refresh every 15 seconds
            </span>
          </div>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Metric
              label="Orders created"
              value={created.toLocaleString()}
              detail="New orders in the selected period"
              icon={<Package size={19} />}
            />
            <Metric
              label="Completed outcomes"
              value={data.outcomes.completed.toLocaleString()}
              detail="Delivered and failed deliveries"
              icon={<CheckCheck size={19} />}
            />
            <Metric
              label="Success rate"
              value={
                data.outcomes.successRatePercent === null
                  ? '—'
                  : `${data.outcomes.successRatePercent.toFixed(2)}%`
              }
              detail={
                data.outcomes.completed
                  ? 'Delivered ÷ completed outcomes'
                  : 'No completed outcomes yet'
              }
              icon={<ArrowUpRight size={19} />}
            />
            <Metric
              label="Current active orders"
              value={data.workload.totalActiveOrders.toLocaleString()}
              detail="Assigned orders still in progress · now"
              icon={<Truck size={19} />}
            />
          </div>
          <SurfaceCard density="compact" className="rounded-xl shadow-sm">
            <div className="flex flex-wrap items-start justify-between gap-3 p-2 pb-5">
              <div>
                <h2 className="text-base font-semibold">Deliveries per day</h2>
              </div>
              <div className="flex gap-4 text-xs text-secondary">
                {series.map((item) => (
                  <span key={item.key} className="inline-flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full" style={{ background: item.color }} />
                    {item.label}
                  </span>
                ))}
              </div>
            </div>
            {!data.daily.days.some((day) => day.created || day.delivered || day.failed) ? (
              <EmptyChart icon={<Package size={24} />} title="No delivery activity in this period">
                Try a wider date range to see earlier orders.
              </EmptyChart>
            ) : (
              <div
                className="h-80 min-w-0 text-xs"
                aria-label="Daily created, delivered and failed counts"
              >
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={data.daily.days}
                    accessibilityLayer
                    margin={{ right: 16, top: 12, left: -12, bottom: 8 }}
                  >
                    <CartesianGrid
                      vertical={false}
                      stroke="var(--color-border-subtle)"
                      strokeDasharray="0"
                    />
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
                      allowDecimals={false}
                      axisLine={false}
                      tickLine={false}
                      tickMargin={8}
                      domain={[0, 'auto']}
                      stroke="var(--color-text-secondary)"
                    />
                    <Tooltip
                      contentStyle={tooltipStyle}
                      labelFormatter={(value) => dateLabel(String(value))}
                    />
                    {series.map((item) => (
                      <Line
                        key={item.key}
                        type="monotone"
                        dataKey={item.key}
                        name={item.label}
                        stroke={item.color}
                        strokeWidth={4}
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeDasharray={item.key === 'failed' ? '5 4' : undefined}
                        dot={{
                          r: 3,
                          fill: item.color,
                          strokeWidth: 1.5,
                          stroke: 'var(--color-surface-card)'
                        }}
                        activeDot={{ r: 6 }}
                        isAnimationActive={false}
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
            <details className="mx-2 mt-3 border-t border-border-subtle pt-3 text-xs text-secondary">
              <summary className="w-fit cursor-pointer font-medium">
                View daily counts as a table
              </summary>
              <div className="overflow-x-auto">
                <table className="mt-3 w-full text-right text-sm tabular-nums">
                  <caption className="sr-only">
                    Daily delivery counts for the selected reporting period
                  </caption>
                  <thead>
                    <tr>
                      {['Date', 'Created', 'Delivered', 'Failed'].map((label) => (
                        <th key={label} scope="col" className="p-2 font-medium">
                          {label}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {data.daily.days.map((day) => (
                      <tr key={day.date} className="border-t border-border-subtle">
                        <th scope="row" className="p-2 font-normal">
                          {dateLabel(day.date)}
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
          <div className="grid gap-5 lg:grid-cols-2">
            <SurfaceCard density="compact" className="rounded-xl shadow-sm">
              <div className="p-2">
                <h2 className="text-base font-semibold">Success vs failure</h2>
              </div>
              {data.outcomes.completed === 0 ? (
                <EmptyChart
                  icon={<CheckCheck size={24} />}
                  title="No completed outcomes in this period"
                >
                  Outcomes appear once orders are delivered or fail.
                </EmptyChart>
              ) : (
                <div className="flex min-h-64 flex-wrap items-center justify-center gap-6 p-2">
                  <div className="relative h-52 w-52">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart accessibilityLayer>
                        <Pie
                          data={outcomes}
                          dataKey="count"
                          nameKey="name"
                          innerRadius={68}
                          outerRadius={88}
                          paddingAngle={3}
                          stroke="none"
                          isAnimationActive={false}
                        >
                          {outcomes.map((item) => (
                            <Cell key={item.name} fill={item.color} />
                          ))}
                        </Pie>
                        <Tooltip contentStyle={tooltipStyle} />
                      </PieChart>
                    </ResponsiveContainer>
                    <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                      <span className="text-2xl font-semibold tabular-nums">
                        {data.outcomes.completed}
                      </span>
                      <span className="text-xs text-secondary">Completed</span>
                    </div>
                  </div>
                  <div className="min-w-36 space-y-5">
                    {outcomes.map((item) => (
                      <div key={item.name}>
                        <div className="flex items-center gap-2 text-sm text-secondary">
                          <span
                            className="size-2 rounded-full"
                            style={{ background: item.color }}
                          />
                          {item.name}
                        </div>
                        <p className="mt-1 text-xl font-semibold tabular-nums">
                          {item.count}
                          <span className="ml-3 text-xs font-normal text-secondary">
                            {((item.count / data.outcomes.completed) * 100).toFixed(1)}%
                          </span>
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              <p className="mx-2 border-t border-border-subtle pt-3 text-xs text-secondary">
                Cancelled and returned orders are excluded.
              </p>
            </SurfaceCard>
            <SurfaceCard density="compact" className="rounded-xl shadow-sm">
              <div className="flex items-start justify-between gap-2 p-2">
                <div>
                  <h2 className="text-base font-semibold">Courier workload now</h2>
                  <p className="mt-1 text-xs text-secondary">Active assigned orders by courier</p>
                </div>
                <span className="rounded-full bg-brand-subtle px-2 py-1 text-xs font-medium text-brand-primary">
                  Current
                </span>
              </div>
              {workload.length === 0 ? (
                <EmptyChart icon={<Truck size={24} />} title="No couriers yet">
                  Workload appears after the first courier assignment.
                  {!isAdmin && (
                    <Link to="/ops" className="mt-3 block font-semibold text-brand-primary">
                      Open control tower →
                    </Link>
                  )}
                </EmptyChart>
              ) : (
                <>
                  <div className="max-h-72 overflow-y-auto">
                    <div
                      className="text-xs"
                      style={{ height: Math.max(240, workload.length * 42) }}
                    >
                      <ResponsiveContainer width="100%" height="100%">
                        <BarChart
                          data={workload}
                          layout="vertical"
                          accessibilityLayer
                          margin={{ right: 24, left: 8, top: 12 }}
                        >
                          <CartesianGrid
                            horizontal={false}
                            stroke="var(--color-border-subtle)"
                            strokeDasharray="0"
                          />
                          <XAxis
                            type="number"
                            allowDecimals={false}
                            axisLine={false}
                            tickLine={false}
                          />
                          <YAxis
                            type="category"
                            dataKey="courierLabel"
                            width={110}
                            axisLine={false}
                            tickLine={false}
                            tickFormatter={(value) =>
                              String(value).length > 17
                                ? `${String(value).slice(0, 16)}…`
                                : String(value)
                            }
                          />
                          <Tooltip
                            contentStyle={tooltipStyle}
                            cursor={{ fill: 'var(--color-surface-canvas)' }}
                          />
                          <Bar
                            dataKey="activeOrders"
                            name="Active orders"
                            fill="var(--color-brand-primary)"
                            radius={[0, 5, 5, 0]}
                            barSize={16}
                            isAnimationActive={false}
                          />
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  </div>
                  <details className="mx-2 my-2 text-xs text-secondary">
                    <summary className="cursor-pointer">View courier IDs and counts</summary>
                    <ul className="mt-2 space-y-1 break-all tabular-nums">
                      {workload.map((courier) => (
                        <li key={courier.courierId}>
                          {courier.courierId}: {courier.activeOrders} active orders
                        </li>
                      ))}
                    </ul>
                  </details>
                </>
              )}
              <p className="mx-2 border-t border-border-subtle pt-3 text-xs text-secondary">
                Includes known idle couriers; does not indicate online availability.
              </p>
            </SurfaceCard>
          </div>
        </>
      )}
      <RevenueReport from={range.from} to={range.to} district={scope} showDistricts={isAdmin} />
    </div>
  );
}
export default AnalyticsDashboardPage;
