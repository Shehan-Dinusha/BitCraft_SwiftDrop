import { useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useInfiniteQuery } from '@tanstack/react-query';
import { FileDown, Plus } from 'lucide-react';
import {
  Alert,
  Button,
  ChoiceChip,
  EmptyState,
  IconButton,
  Input,
  OrderCard,
  PageHeader,
  Skeleton
} from '@/design-system';
import { orderApi, readOrderApiError, type CustomerOrderResponse } from '@/services/api/orderApi';
import {
  STATUS_GROUPS,
  formatDate,
  hasPendingFeeRevision,
  inStatusGroup,
  listedFee,
  orderStatus,
  toSpeed,
  type StatusGroup
} from '../components/orders/orderDisplay';
import { useReceiptDownload } from '../components/orders/useReceiptDownload';
import { usePageTitle } from '../components/usePageTitle';

const PAGE_SIZE = 20;

function cardMeta(order: CustomerOrderResponse) {
  const parts = [
    hasPendingFeeRevision(order) ? 'Action needed: confirm the new fee' : null,
    order.recipientName ? `To ${order.recipientName}` : null,
    order.createdAt ? `Booked ${formatDate(order.createdAt)}` : null
  ];
  return parts.filter(Boolean).join(' · ');
}

function matches(order: CustomerOrderResponse, term: string) {
  if (!term) return true;
  return [order.trackingNumber, order.recipientName, order.deliveryCity, order.pickupCity].some(
    (value) => value?.toLowerCase().includes(term)
  );
}

export function OrderHistoryPage() {
  const navigate = useNavigate();
  const [group, setGroup] = useState<StatusGroup>('all');
  const [search, setSearch] = useState('');
  const receipts = useReceiptDownload();
  const searchRef = useRef<HTMLInputElement>(null);
  usePageTitle('My parcels');

  const ordersQuery = useInfiniteQuery({
    queryKey: ['customer-orders'],
    queryFn: ({ pageParam }) => orderApi.listOrders(pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    retry: (count, error) => count < 2 && (readOrderApiError(error).status ?? 500) >= 500
  });

  const orders = useMemo(
    () => ordersQuery.data?.pages.flatMap((page) => page.content) ?? [],
    [ordersQuery.data]
  );
  const term = search.trim().toLowerCase();
  const searched = useMemo(() => orders.filter((order) => matches(order, term)), [orders, term]);
  // Parcels waiting on the customer come first; the rest keep the server's newest-first order.
  const visible = useMemo(
    () =>
      searched
        .filter((order) => inStatusGroup(order, group))
        .sort((a, b) => Number(hasPendingFeeRevision(b)) - Number(hasPendingFeeRevision(a))),
    [searched, group]
  );
  const needsAnswer = orders.filter(hasPendingFeeRevision);
  const filtersActive = group !== 'all' || term !== '';

  const clearFilters = () => {
    setGroup('all');
    setSearch('');
    // The button that had focus disappears with the empty state.
    window.requestAnimationFrame(() => searchRef.current?.focus());
  };

  const errorStatus = ordersQuery.isError ? readOrderApiError(ordersQuery.error).status : undefined;
  const isEmpty = ordersQuery.isSuccess && orders.length === 0;

  return (
    <div className="flex w-full flex-col gap-5 p-4 sm:p-6 lg:px-8">
      <PageHeader
        title="My parcels"
        className="border-0 bg-transparent p-0"
        subtitle="Follow your parcels, answer fee changes and download labels and receipts."
        actions={
          isEmpty ? undefined : (
            <Button type="button" leftIcon={<Plus />} onClick={() => navigate('/customer/book')}>
              Book a parcel
            </Button>
          )
        }
      />

      {needsAnswer.length > 0 ? (
        <Alert
          tone="warning"
          title={
            needsAnswer.length === 1
              ? 'A parcel needs your answer'
              : `${needsAnswer.length} parcels need your answer`
          }
          action={
            <Button
              type="button"
              variant="ghost"
              onClick={() => navigate(`/customer/orders/${needsAnswer[0].orderId}`)}
            >
              Review {needsAnswer[0].trackingNumber}
            </Button>
          }
        >
          The courier measured the parcel at pickup and the fee changed. Delivery continues once you
          accept or decline.
        </Alert>
      ) : null}

      {orders.length > 0 ? (
        <div className="flex flex-col gap-3">
          <Input
            ref={searchRef}
            variant="search"
            aria-label="Search parcels"
            placeholder="Search by tracking number, recipient or city"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            onClear={() => setSearch('')}
            className="sm:max-w-md"
          />
          <div role="group" aria-label="Filter by status" className="flex flex-wrap gap-2">
            {STATUS_GROUPS.map((item) => {
              const count = searched.filter((order) => inStatusGroup(order, item.id)).length;
              return (
                <ChoiceChip
                  key={item.id}
                  selected={group === item.id}
                  onClick={() => setGroup(item.id)}
                >
                  {item.label} ({count})
                </ChoiceChip>
              );
            })}
          </div>
          <p className="sr-only" aria-live="polite">
            {visible.length === 0
              ? 'No parcels match'
              : visible.length === 1
                ? '1 parcel shown'
                : `${visible.length} parcels shown`}
          </p>
        </div>
      ) : null}

      {receipts.failure ? (
        <Alert
          tone="danger"
          title="We couldn't download the label and receipt"
          onDismiss={receipts.clearFailure}
        >
          {receipts.failure.status === 404
            ? 'It is not available for this parcel.'
            : 'Try again in a moment.'}
        </Alert>
      ) : null}

      <section aria-label="Parcels" aria-busy={ordersQuery.isPending}>
        {ordersQuery.isPending ? (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 4 }, (_, index) => (
              <Skeleton key={index} preset="card" className="w-full max-w-none" />
            ))}
          </div>
        ) : ordersQuery.isError ? (
          errorStatus === 401 ? (
            <Alert
              tone="warning"
              title="Please sign in again"
              action={
                <Button type="button" variant="ghost" onClick={() => navigate('/login')}>
                  Sign in
                </Button>
              }
            >
              Your session has ended.
            </Alert>
          ) : (
            <EmptyState
              type="offline"
              title="We couldn't load your parcels"
              message={
                errorStatus === undefined
                  ? 'Check your internet connection and try again.'
                  : 'Something went wrong on our side. Try again in a moment.'
              }
              action={{ label: 'Try again', onClick: () => void ordersQuery.refetch() }}
              className="mx-auto"
            />
          )
        ) : isEmpty ? (
          <EmptyState
            type="empty-queue"
            title="No parcels yet"
            message="Parcels you book appear here with their status, label and receipt."
            action={{ label: 'Book a parcel', onClick: () => navigate('/customer/book') }}
            className="mx-auto"
          />
        ) : visible.length === 0 ? (
          <EmptyState
            type="no-results"
            title="No parcels match"
            message="Try another search or status."
            action={{ label: 'Clear filters', onClick: clearFilters }}
            className="mx-auto"
          />
        ) : (
          <ul className="flex flex-col gap-3">
            {visible.map((order) => {
              const trackingNumber = order.trackingNumber ?? '';
              const status = orderStatus(order);
              return (
                <li key={order.orderId ?? trackingNumber} className="flex items-center gap-2">
                  <OrderCard
                    type="customer"
                    trackingNumber={trackingNumber}
                    status={status}
                    from={order.pickupCity ?? ''}
                    to={order.deliveryCity ?? ''}
                    meta={cardMeta(order)}
                    speed={toSpeed(order.orderSpeed)}
                    fee={listedFee(order)}
                    onOpen={() => navigate(`/customer/orders/${order.orderId}`)}
                    className="min-w-0 max-w-none flex-1"
                  />
                  {status !== 'CANCELLED' ? (
                    <IconButton
                      variant="ghost"
                      icon={<FileDown />}
                      aria-label={`Download label and receipt for ${trackingNumber}`}
                      title="Download label and receipt (PDF)"
                      loading={receipts.isPending(order.orderId)}
                      onClick={() => void receipts.download(order)}
                    />
                  ) : (
                    // Keeps the cards aligned when there is no label to download.
                    <span aria-hidden="true" className="w-11 shrink-0" />
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {ordersQuery.hasNextPage ? (
        <div className="flex flex-col items-center gap-2">
          <Button
            type="button"
            variant="secondary"
            loading={ordersQuery.isFetchingNextPage}
            loadingLabel="Loading more parcels"
            onClick={() => void ordersQuery.fetchNextPage()}
          >
            Show more parcels
          </Button>
          {filtersActive ? (
            <p className="text-caption text-secondary">
              Search and filters cover the parcels loaded so far.
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

export default OrderHistoryPage;
