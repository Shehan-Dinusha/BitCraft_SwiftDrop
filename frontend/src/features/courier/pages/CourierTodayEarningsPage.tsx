import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useCourierTodayStats,
  calculateDeliveryEarning,
  isInterDistrict,
  formatLKR
} from '../hooks/useCourierTodayStats';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { FsmStatusBadge } from '@/design-system/components/FsmStatusBadge/FsmStatusBadge';
import { Button } from '@/design-system/primitives/Button';
import {
  Wallet,
  Package,
  RefreshCw,
  Search,
  CheckCircle2,
  Calendar,
  MapPin,
  ArrowRight,
  Receipt
} from 'lucide-react';

export function CourierTodayEarningsPage() {
  const navigate = useNavigate();
  const {
    deliveries,
    deliveredDeliveries,
    deliveredCount,
    totalEarnings,
    loading,
    refresh
  } = useCourierTodayStats();

  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const handleRefresh = async () => {
    setRefreshing(true);
    await refresh();
    setRefreshing(false);
  };

  // Filtered deliveries for itemized list based on search query
  const filteredList = useMemo(() => {
    if (!searchQuery.trim()) return deliveredDeliveries;
    const query = searchQuery.trim().toLowerCase();
    return deliveredDeliveries.filter((item) => {
      const trackingMatch = item.trackingNumber?.toLowerCase().includes(query);
      const pickupMatch = item.pickupDistrict?.toLowerCase().includes(query);
      const deliveryMatch = item.deliveryDistrict?.toLowerCase().includes(query);
      return trackingMatch || pickupMatch || deliveryMatch;
    });
  }, [deliveredDeliveries, searchQuery]);

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
            Today's Earnings
          </h1>
          <p className="mt-0.5 text-sm text-secondary">
            Total payout and prices earned from your completed deliveries today
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            onClick={handleRefresh}
            disabled={loading || refreshing}
            className="flex items-center gap-2"
          >
            <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin text-brand-primary' : ''}`} />
            <span>Refresh</span>
          </Button>
          <Button
            variant="primary"
            onClick={() => navigate('/courier/today-deliveries')}
            className="flex items-center gap-1.5"
          >
            <span>Today's Deliveries</span>
            <ArrowRight className="h-4 w-4" />
          </Button>
        </div>
      </header>

      {/* Primary Highlight Metrics */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {/* Total Earned Card */}
        <SurfaceCard className="relative overflow-hidden border-brand-primary/40 p-5 shadow-sm transition-all hover:border-brand-primary">
          <div className="flex items-center justify-between text-secondary">
            <span className="text-xs font-semibold uppercase tracking-wider text-secondary">
              Total Earned Today
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-status-success-bg text-status-success-fg">
              <Wallet className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3 text-3xl font-extrabold tracking-tight text-status-success-fg">
            {formatLKR(totalEarnings)}
          </div>
          <p className="mt-1.5 text-xs text-secondary">
            From {deliveredCount} delivered {deliveredCount === 1 ? 'item' : 'items'}
          </p>
        </SurfaceCard>

        {/* Delivered Items Count */}
        <SurfaceCard className="p-5 transition-all hover:border-brand-primary">
          <div className="flex items-center justify-between text-secondary">
            <span className="text-xs font-semibold uppercase tracking-wider text-secondary">
              Delivered Items
            </span>
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary">
              <Package className="h-4 w-4" />
            </div>
          </div>
          <div className="mt-3 text-3xl font-extrabold text-primary">{deliveredCount}</div>
          <p className="mt-1.5 text-xs text-secondary">Completed deliveries today</p>
        </SurfaceCard>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-2">
          <span className="rounded-lg bg-brand-primary px-3 py-1.5 text-xs font-semibold text-white shadow-sm">
            All Delivered ({deliveredCount})
          </span>
        </div>

        {/* Search Input */}
        <div className="relative min-w-[240px]">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-secondary" />
          <input
            type="text"
            placeholder="Search by tracking number or district…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full rounded-lg border border-border-subtle bg-card py-1.5 pl-9 pr-3 text-xs text-primary placeholder-secondary focus:border-brand-primary focus:outline-none focus:ring-1 focus:ring-brand-primary"
          />
        </div>
      </div>

      {/* Deliveries Itemized Earnings List */}
      {filteredList.length === 0 ? (
        <SurfaceCard className="flex flex-col items-center justify-center p-12 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-canvas text-secondary">
            <Receipt className="h-6 w-6" />
          </div>
          <h3 className="mt-4 text-base font-bold text-primary">No delivered items found</h3>
          <p className="mt-1 max-w-sm text-xs text-secondary">
            {deliveredDeliveries.length === 0
              ? 'You have not completed any deliveries yet today. Complete your active deliveries to see your earned prices here.'
              : 'No delivered items match the current search query.'}
          </p>
          {deliveredDeliveries.length === 0 && (
            <div className="mt-4 flex gap-2">
              <Button
                variant="primary"
                onClick={() => navigate('/courier/delivery')}
                className="flex items-center gap-1.5 text-xs"
              >
                <span>Go to Active Delivery</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Button>
            </div>
          )}
        </SurfaceCard>
      ) : (
        <div className="space-y-3">
          {filteredList.map((item) => {
            const earning = calculateDeliveryEarning(item);
            const isCross = isInterDistrict(item);

            return (
              <SurfaceCard
                key={item.deliveryId}
                className="group relative flex flex-col justify-between gap-4 p-4 transition-all hover:border-brand-primary sm:flex-row sm:items-center"
              >
                {/* Left: Info, Route & Tracking */}
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-bold text-primary">
                      {item.trackingNumber || item.deliveryId?.slice(0, 8)}
                    </span>
                    <FsmStatusBadge state={item.currentState || 'DELIVERED'} />
                    <span
                      className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${
                        isCross
                          ? 'bg-brand-subtle text-brand-primary'
                          : 'bg-card text-secondary border border-border-subtle'
                      }`}
                    >
                      {isCross ? 'Inter-District (Long Haul)' : 'Intra-District'}
                    </span>
                  </div>

                  {/* Route details */}
                  <div className="flex items-center gap-2 text-xs text-secondary">
                    <MapPin className="h-3.5 w-3.5 text-brand-primary shrink-0" />
                    <span className="font-medium text-primary">
                      {item.pickupDistrict || 'Origin'}
                    </span>
                    <span className="text-secondary">→</span>
                    <span className="font-medium text-primary">
                      {item.deliveryDistrict || 'Destination'}
                    </span>
                    {item.deliveredAt && (
                      <>
                        <span className="text-border-subtle">·</span>
                        <span className="flex items-center gap-1 text-[11px] text-secondary">
                          <CheckCircle2 className="h-3 w-3 text-status-success-fg" />
                          Delivered at {formatDate(item.deliveredAt)}
                        </span>
                      </>
                    )}
                  </div>
                </div>

                {/* Right: Payout and Action */}
                <div className="flex items-center justify-between gap-4 border-t border-border-subtle pt-3 sm:border-0 sm:pt-0">
                  <div className="text-right">
                    <div className="flex items-center gap-1 font-mono text-base font-extrabold text-status-success-fg sm:text-lg">
                      <span>+{formatLKR(earning)}</span>
                    </div>
                    <span className="text-[10px] text-secondary">Completed drop payout</span>
                  </div>

                  <Button
                    variant="secondary"
                    onClick={() =>
                      item.deliveryId
                        ? navigate(`/courier/delivery/${item.deliveryId}`)
                        : navigate('/courier/today-deliveries')
                    }
                    className="flex items-center gap-1 text-xs"
                  >
                    <span>Details</span>
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </SurfaceCard>
            );
          })}
        </div>
      )}

      {/* Summary Footer Card */}
      <SurfaceCard className="flex flex-col items-center justify-between gap-3 border-dashed p-4 text-xs text-secondary sm:flex-row">
        <div>
          <span className="font-semibold text-primary">Daily Total Summary:</span>{' '}
          {deliveredCount} delivered out of {deliveries.length} assigned jobs today · Total earnings:{' '}
          <span className="font-bold text-status-success-fg">{formatLKR(totalEarnings)}</span>
        </div>
        <Button
          variant="secondary"
          onClick={() => navigate('/courier')}
          className="text-xs"
        >
          Return to Shift
        </Button>
      </SurfaceCard>
    </main>
  );
}
