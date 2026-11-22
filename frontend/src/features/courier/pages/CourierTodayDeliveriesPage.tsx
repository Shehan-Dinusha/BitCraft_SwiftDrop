import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { deliveryApi, type DeliverySessionResponse } from '@/services/api/deliveryApi';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { Button } from '@/design-system/primitives/Button';
import {
  Package,
  RefreshCw,
  Search,
  CheckCircle2,
  Clock,
  AlertTriangle,
  ChevronRight,
  ArrowRight,
  Calendar,
  MapPin
} from 'lucide-react';

type FilterTab = 'all' | 'active' | 'delivered' | 'failed';

export function CourierTodayDeliveriesPage() {
  const navigate = useNavigate();
  const [deliveries, setDeliveries] = useState<DeliverySessionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<FilterTab>('all');
  const [searchQuery, setSearchQuery] = useState('');

  const loadDeliveries = useCallback(async (isRefresh = false) => {
    try {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setErrorMsg(null);

      const data = await deliveryApi.getMyDeliveries('today');
      setDeliveries(data || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to load today’s deliveries.';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    loadDeliveries();
  }, [loadDeliveries]);

  // Counts for summary metrics
  const stats = useMemo(() => {
    const total = deliveries.length;
    const delivered = deliveries.filter((d) => d.currentState === 'DELIVERED').length;
    const failed = deliveries.filter((d) => d.currentState === 'FAILED' || d.currentState === 'CANCELLED').length;
    const active = total - delivered - failed;
    return { total, active, delivered, failed };
  }, [deliveries]);

  // Filtered deliveries based on tab and search
  const filteredDeliveries = useMemo(() => {
    return deliveries.filter((item) => {
      // Tab filter
      if (activeTab === 'active') {
        if (item.currentState === 'DELIVERED' || item.currentState === 'FAILED' || item.currentState === 'CANCELLED') {
          return false;
        }
      } else if (activeTab === 'delivered') {
        if (item.currentState !== 'DELIVERED') return false;
      } else if (activeTab === 'failed') {
        if (item.currentState !== 'FAILED' && item.currentState !== 'CANCELLED') return false;
      }

      // Search query filter
      if (searchQuery.trim()) {
        const query = searchQuery.trim().toLowerCase();
        const trackingMatch = item.trackingNumber?.toLowerCase().includes(query);
        const pickupMatch = item.pickupDistrict?.toLowerCase().includes(query);
        const deliveryMatch = item.deliveryDistrict?.toLowerCase().includes(query);
        const statusMatch = item.currentState?.toLowerCase().includes(query);
        return trackingMatch || pickupMatch || deliveryMatch || statusMatch;
      }

      return true;
    });
  }, [deliveries, activeTab, searchQuery]);

  const formatDate = (isoString?: string) => {
    if (!isoString) return '—';
    try {
      return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return isoString;
    }
  };

  const todayDateString = new Intl.DateTimeFormat('en-US', {
    weekday: 'long',
    month: 'short',
    day: 'numeric',
    year: 'numeric'
  }).format(new Date());

  return (
    <main className="mx-auto max-w-5xl space-y-6 p-4 md:p-6 pb-24" data-density="kinetic">
      {/* Top Header */}
      <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-secondary">
            <Calendar className="h-3.5 w-3.5 text-brand-primary" />
            <span>{todayDateString}</span>
          </div>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-primary md:text-3xl">
            Today's Deliveries
          </h1>
          <p className="mt-0.5 text-sm text-secondary">
            View, track, and manage all assigned deliveries for your current shift
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            onClick={() => loadDeliveries(true)}
            disabled={loading || refreshing}
            className="flex items-center gap-2"
          >
            <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin text-brand-primary' : ''}`} />
            <span>Refresh</span>
          </Button>
          <Button
            variant="primary"
            onClick={() => navigate('/courier')}
            className="flex items-center gap-1.5"
          >
            <span>Shift Dashboard</span>
          </Button>
        </div>
      </header>

      {/* Metric Cards */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <SurfaceCard className="p-4 transition-all hover:border-brand-primary">
          <div className="flex items-center justify-between text-secondary">
            <span className="text-xs font-medium">Total Today</span>
            <Package className="h-4 w-4 text-brand-primary" />
          </div>
          <div className="mt-2 text-2xl font-bold text-primary">{stats.total}</div>
          <p className="mt-1 text-xs text-secondary">All scheduled</p>
        </SurfaceCard>

        <SurfaceCard className="p-4 transition-all hover:border-brand-primary">
          <div className="flex items-center justify-between text-secondary">
            <span className="text-xs font-medium">Active / Pending</span>
            <Clock className="h-4 w-4 text-status-warning-fg" />
          </div>
          <div className="mt-2 text-2xl font-bold text-status-warning-fg">{stats.active}</div>
          <p className="mt-1 text-xs text-secondary">In transit / out</p>
        </SurfaceCard>

        <SurfaceCard className="p-4 transition-all hover:border-brand-primary">
          <div className="flex items-center justify-between text-secondary">
            <span className="text-xs font-medium">Completed</span>
            <CheckCircle2 className="h-4 w-4 text-status-success-fg" />
          </div>
          <div className="mt-2 text-2xl font-bold text-status-success-fg">{stats.delivered}</div>
          <p className="mt-1 text-xs text-secondary">Successfully delivered</p>
        </SurfaceCard>

        <SurfaceCard className="p-4 transition-all hover:border-brand-primary">
          <div className="flex items-center justify-between text-secondary">
            <span className="text-xs font-medium">Exceptions</span>
            <AlertTriangle className="h-4 w-4 text-status-danger-fg" />
          </div>
          <div className="mt-2 text-2xl font-bold text-status-danger-fg">{stats.failed}</div>
          <p className="mt-1 text-xs text-secondary">Failed / Cancelled</p>
        </SurfaceCard>
      </div>

      {/* Filter Tabs and Search Bar */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
          <button
            type="button"
            onClick={() => setActiveTab('all')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'all'
                ? 'bg-brand-primary text-white shadow-sm'
                : 'bg-card text-secondary hover:bg-canvas hover:text-primary'
            }`}
          >
            All ({stats.total})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('active')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'active'
                ? 'bg-brand-primary text-white shadow-sm'
                : 'bg-card text-secondary hover:bg-canvas hover:text-primary'
            }`}
          >
            Active ({stats.active})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('delivered')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'delivered'
                ? 'bg-brand-primary text-white shadow-sm'
                : 'bg-card text-secondary hover:bg-canvas hover:text-primary'
            }`}
          >
            Delivered ({stats.delivered})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('failed')}
            className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-all ${
              activeTab === 'failed'
                ? 'bg-brand-primary text-white shadow-sm'
                : 'bg-card text-secondary hover:bg-canvas hover:text-primary'
            }`}
          >
            Failed ({stats.failed})
          </button>
        </div>

        <div className="relative min-w-[240px]">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search by tracking number or district..."
            className="w-full rounded-lg border border-border-subtle bg-card py-1.5 pl-9 pr-3 text-xs text-primary placeholder:text-muted focus:border-brand-primary focus:outline-none focus:ring-1 focus:ring-brand-primary"
          />
          {searchQuery && (
            <button
              type="button"
              onClick={() => setSearchQuery('')}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-xs text-muted hover:text-primary"
            >
              &times;
            </button>
          )}
        </div>
      </div>

      {/* Error Banner */}
      {errorMsg && (
        <SurfaceCard className="border-status-danger-border bg-status-danger-bg p-4 text-status-danger-fg">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-5 w-5 shrink-0" />
              <p className="text-sm font-medium">{errorMsg}</p>
            </div>
            <Button variant="secondary" onClick={() => loadDeliveries()}>
              Retry
            </Button>
          </div>
        </SurfaceCard>
      )}

      {/* Deliveries List */}
      <section className="space-y-3">
        {loading ? (
          <div className="space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <SurfaceCard key={i} className="animate-pulse p-4">
                <div className="flex items-center justify-between">
                  <div className="h-5 w-40 rounded bg-border-subtle" />
                  <div className="h-6 w-20 rounded-full bg-border-subtle" />
                </div>
                <div className="mt-3 h-4 w-60 rounded bg-border-subtle" />
              </SurfaceCard>
            ))}
          </div>
        ) : filteredDeliveries.length === 0 ? (
          <SurfaceCard className="p-10 text-center">
            <Package className="mx-auto h-12 w-12 text-muted" />
            <h3 className="mt-3 text-base font-bold text-primary">
              {deliveries.length === 0 ? 'No deliveries today' : 'No matching deliveries'}
            </h3>
            <p className="mt-1 text-sm text-secondary">
              {deliveries.length === 0
                ? 'You do not have any deliveries assigned for today yet. Start or check your shift dashboard.'
                : 'Try adjusting your search query or filter tab.'}
            </p>
            {deliveries.length > 0 && (
              <Button
                variant="secondary"
                className="mt-4"
                onClick={() => {
                  setActiveTab('all');
                  setSearchQuery('');
                }}
              >
                Clear filters
              </Button>
            )}
          </SurfaceCard>
        ) : (
          <div className="space-y-3">
            {filteredDeliveries.map((delivery) => {
              const isDelivered = delivery.currentState === 'DELIVERED';

              return (
                <SurfaceCard
                  key={delivery.deliveryId}
                  className="group relative flex flex-col justify-between gap-4 p-4 transition-all hover:border-brand-primary sm:flex-row sm:items-center"
                >
                  <div className="space-y-2 min-w-0 flex-1">
                    {/* Header Row: Tracking Number + Badge */}
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-base font-bold tracking-tight text-primary">
                        {delivery.trackingNumber}
                      </span>
                      <FsmStatusBadge state={delivery.currentState || 'ASSIGNED'} size="sm" />
                      {delivery.attemptCount && delivery.attemptCount > 0 ? (
                        <span className="rounded bg-brand-subtle px-2 py-0.5 text-[10px] font-semibold text-brand-primary">
                          Attempt #{delivery.attemptCount}
                        </span>
                      ) : null}
                    </div>

                    {/* Route Details */}
                    <div className="flex items-center gap-2 text-xs text-secondary">
                      <div className="flex items-center gap-1 text-primary font-medium">
                        <MapPin className="h-3.5 w-3.5 text-muted shrink-0" />
                        <span>{delivery.pickupDistrict}</span>
                      </div>
                      <ArrowRight className="h-3.5 w-3.5 text-muted shrink-0" />
                      <div className="flex items-center gap-1 text-primary font-bold">
                        <span>{delivery.deliveryDistrict}</span>
                      </div>
                    </div>

                    {/* Timestamps & Extra Info */}
                    <div className="flex flex-wrap items-center gap-3 text-[11px] text-muted">
                      {delivery.pickupAt && (
                        <span>
                          Picked up: <strong className="text-secondary">{formatDate(delivery.pickupAt)}</strong>
                        </span>
                      )}
                      {delivery.deliveredAt && (
                        <span>
                          Delivered: <strong className="text-secondary">{formatDate(delivery.deliveredAt)}</strong>
                        </span>
                      )}
                      {delivery.updatedAt && !delivery.deliveredAt && (
                        <span>
                          Updated: <strong className="text-secondary">{formatDate(delivery.updatedAt)}</strong>
                        </span>
                      )}
                      {delivery.failedReason && (
                        <span className="text-status-danger-fg font-medium">
                          Reason: {delivery.failedReason}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Action Button */}
                  <div className="flex items-center gap-2 shrink-0 self-end sm:self-center">
                    <Button
                      variant={isDelivered ? 'secondary' : 'primary'}
                      onClick={() => navigate(`/courier/delivery/${delivery.deliveryId}`)}
                      className="flex items-center gap-1.5"
                    >
                      <span>{isDelivered ? 'View Details' : 'Execute Delivery'}</span>
                      <ChevronRight className="h-4 w-4" />
                    </Button>
                  </div>
                </SurfaceCard>
              );
            })}
          </div>
        )}
      </section>
    </main>
  );
}

export default CourierTodayDeliveriesPage;
