import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileDown, Receipt, Route } from 'lucide-react';
import {
  Alert,
  Button,
  EmptyState,
  FsmStatusBadge,
  MoneyAmount,
  PageHeader,
  Skeleton,
  SpeedTag,
  SurfaceCard
} from '@/design-system';
import { orderApi, readOrderApiError, type CustomerOrderResponse } from '@/services/api/orderApi';
import { placeLabel } from '../components/booking/bookingForm';
import {
  canCancel,
  formatDate,
  hasPendingFeeRevision,
  isUuid,
  orderStatus,
  STATUS_LABEL,
  toSpeed
} from '../components/orders/orderDisplay';
import { usePageTitle } from '../components/usePageTitle';
import { useReceiptDownload } from '../components/orders/useReceiptDownload';
import { OrderStatusSection } from '../components/orders/OrderStatusSection';
import { OrderFeeSection } from '../components/orders/OrderFeeSection';
import { OrderDetailsSection } from '../components/orders/OrderDetailsSection';
import { CancelDialog, DeclineFeeDialog } from '../components/orders/OrderActionDialogs';

type ActionProblem = 'state' | 'invalid' | 'failed';
type Action = 'cancel' | 'accept' | 'decline';

const ACTION_TITLE: Record<Action, string> = {
  cancel: "We couldn't cancel this parcel",
  accept: "We couldn't accept the new fee",
  decline: "We couldn't decline the new fee"
};

const ACTION_COPY: Record<ActionProblem, string> = {
  state: 'This parcel changed since you opened it, so we refreshed the details.',
  invalid: 'Check your answer and try again.',
  failed: 'Something went wrong on our side. Try again in a moment.'
};

function problemFor(error: unknown): ActionProblem {
  const { status } = readOrderApiError(error);
  if (status === 409) return 'state';
  if (status === 400) return 'invalid';
  return 'failed';
}

const PAGE = 'mx-auto flex w-full max-w-4xl flex-col gap-5 p-4 sm:p-6';

export function OrderDetailPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const receipts = useReceiptDownload();
  const validId = isUuid(orderId);

  const [cancelOpen, setCancelOpen] = useState(false);
  const [declineOpen, setDeclineOpen] = useState(false);
  const [problem, setProblem] = useState<{ action: Action; kind: ActionProblem } | null>(null);
  const openerRef = useRef<HTMLElement | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [focusTarget, setFocusTarget] = useState<'status' | 'notice' | 'problem' | null>(null);
  const statusRef = useRef<HTMLDivElement>(null);
  const noticeRef = useRef<HTMLDivElement>(null);
  const problemRef = useRef<HTMLDivElement>(null);

  const orderQuery = useQuery({
    queryKey: ['customer-order', orderId],
    queryFn: () => orderApi.getOrderById(orderId as string),
    enabled: validId,
    retry: (count, error) => count < 2 && (readOrderApiError(error).status ?? 500) >= 500
  });

  // The history is extra detail: if it cannot be loaded the page simply leaves it out.
  const historyQuery = useQuery({
    queryKey: ['customer-order-history', orderId],
    queryFn: () => orderApi.getStatusHistory(orderId as string),
    enabled: validId,
    retry: false
  });

  // After an action closes its dialog, the button that opened it may be gone, so move focus to
  // the part of the page that changed (WCAG 2.4.3).
  useEffect(() => {
    if (!focusTarget) return;
    const target = { status: statusRef, notice: noticeRef, problem: problemRef }[focusTarget]
      .current;
    target?.focus();
    setFocusTarget(null);
  }, [focusTarget, notice, problem, orderQuery.data]);

  const applyUpdate = (order: CustomerOrderResponse, message: string | null) => {
    queryClient.setQueryData(['customer-order', orderId], order);
    void queryClient.invalidateQueries({ queryKey: ['customer-order-history', orderId] });
    void queryClient.invalidateQueries({ queryKey: ['customer-orders'] });
    setProblem(null);
    setNotice(message);
    setFocusTarget(message ? 'notice' : 'status');
  };
  const handleActionError = (action: Action) => (error: unknown) => {
    const kind = problemFor(error);
    setNotice(null);
    setProblem({ action, kind });
    setFocusTarget('problem');
    if (kind === 'state') void orderQuery.refetch();
  };

  // Dialogs remember the button that opened them and hand focus back when dismissed.
  const openDialog = (open: (value: boolean) => void) => {
    openerRef.current = document.activeElement as HTMLElement | null;
    open(true);
  };
  const dismissDialog = (open: (value: boolean) => void) => {
    open(false);
    window.requestAnimationFrame(() => openerRef.current?.focus());
  };

  const cancelMutation = useMutation({
    mutationFn: (reason: string) => orderApi.cancelOrder(orderId as string, reason),
    onSuccess: (order) => {
      setCancelOpen(false);
      // The status card now says it was cancelled; a second message would repeat it.
      applyUpdate(order, null);
    },
    onError: (error) => {
      setCancelOpen(false);
      handleActionError('cancel')(error);
    }
  });
  const acceptMutation = useMutation({
    mutationFn: () => orderApi.acceptFeeRevision(orderId as string),
    onSuccess: (order) => applyUpdate(order, 'New fee accepted. Delivery continues.'),
    onError: handleActionError('accept')
  });
  const declineMutation = useMutation({
    mutationFn: () => orderApi.rejectFeeRevision(orderId as string),
    onSuccess: (order) => {
      setDeclineOpen(false);
      applyUpdate(order, null);
    },
    onError: (error) => {
      setDeclineOpen(false);
      handleActionError('decline')(error);
    }
  });
  const revisionBusy = acceptMutation.isPending || declineMutation.isPending;

  const backToList = () => navigate('/customer/orders');
  usePageTitle(
    orderQuery.data?.trackingNumber ? `${orderQuery.data.trackingNumber} · My parcels` : undefined
  );
  const errorStatus = orderQuery.isError ? readOrderApiError(orderQuery.error).status : undefined;

  if (!validId || errorStatus === 404) {
    return (
      <div className={PAGE}>
        <EmptyState
          type="no-results"
          title="Parcel not found"
          message="Check the link, or find the parcel in My parcels."
          action={{ label: 'Go to My parcels', onClick: backToList }}
          className="mx-auto"
        />
      </div>
    );
  }

  if (orderQuery.isError) {
    return (
      <div className={PAGE}>
        {errorStatus === 401 ? (
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
            title="We couldn't load this parcel"
            message={
              errorStatus === undefined
                ? 'Check your internet connection and try again.'
                : 'Something went wrong on our side. Try again in a moment.'
            }
            action={{ label: 'Try again', onClick: () => void orderQuery.refetch() }}
            className="mx-auto"
          />
        )}
      </div>
    );
  }

  const order = orderQuery.data;
  if (!order) {
    return (
      <div className={PAGE} aria-busy="true">
        <span className="sr-only" role="status">
          Loading parcel
        </span>
        <Skeleton preset="text-block" />
        <Skeleton preset="card" className="w-full max-w-none" />
        <Skeleton preset="card" className="w-full max-w-none" />
      </div>
    );
  }

  const status = orderStatus(order);
  const trackingNumber = order.trackingNumber ?? '';
  const pendingRevision = hasPendingFeeRevision(order);
  const beforePickup = status === 'CREATED' || status === 'PICKUP_ASSIGNED';
  const route = `${placeLabel(order.pickupCity, order.pickupDistrict)} to ${placeLabel(order.deliveryCity, order.deliveryDistrict)}`;

  const trackButton = (primary: boolean) => (
    <Button
      type="button"
      variant={primary ? 'primary' : 'secondary'}
      leftIcon={<Route />}
      onClick={() => navigate(`/customer/tracking/${encodeURIComponent(trackingNumber)}`)}
    >
      Track parcel
    </Button>
  );
  const labelButton = (primary: boolean) => (
    <Button
      type="button"
      variant={primary ? 'primary' : 'secondary'}
      leftIcon={<FileDown />}
      loading={receipts.isPending(order.orderId)}
      loadingLabel="Downloading label and receipt"
      onClick={() => void receipts.download(order)}
    >
      Label and receipt
    </Button>
  );
  const deliveryReceiptButton = (
    <Button
      type="button"
      leftIcon={<Receipt />}
      loading={receipts.isPending(order.orderId, 'delivery-receipt')}
      loadingLabel="Downloading delivery receipt"
      onClick={() => void receipts.download(order, 'delivery-receipt')}
    >
      Delivery receipt
    </Button>
  );

  // One primary action per state: print the label before pickup, follow the parcel while it moves,
  // keep the receipt once delivered. Cancelled parcels have nothing left to do.
  // While a fee change waits for an answer, that answer is the primary action, not the header.
  const lead = !pendingRevision;
  const actions =
    status === 'CANCELLED' ? null : beforePickup ? (
      <>
        {labelButton(lead)}
        {trackButton(false)}
      </>
    ) : status === 'DELIVERED' ? (
      <>
        {deliveryReceiptButton}
        {labelButton(false)}
      </>
    ) : (
      <>
        {trackButton(lead)}
        {labelButton(false)}
      </>
    );

  return (
    <div className={PAGE}>
      <PageHeader
        className="border-0 bg-transparent p-0"
        breadcrumb={[{ label: 'My parcels', onClick: backToList }, { label: trackingNumber }]}
        title={<span className="font-mono tabular-nums">{trackingNumber}</span>}
        badges={
          <>
            <FsmStatusBadge state={status} label={STATUS_LABEL[status]} />
            <SpeedTag speed={toSpeed(order.orderSpeed)} />
          </>
        }
        subtitle={`${route} · Booked ${formatDate(order.createdAt, true)}`}
        actions={actions}
      />

      <div ref={noticeRef} tabIndex={-1} className="outline-none empty:-mt-5" aria-live="polite">
        {notice ? <Alert tone="success" title={notice} onDismiss={() => setNotice(null)} /> : null}
      </div>
      <div ref={problemRef} tabIndex={-1} className="flex flex-col gap-3 outline-none empty:-mt-5">
        {problem ? (
          <Alert
            tone="danger"
            title={ACTION_TITLE[problem.action]}
            onDismiss={() => setProblem(null)}
          >
            {ACTION_COPY[problem.kind]}
          </Alert>
        ) : null}
        {receipts.failure ? (
          <Alert
            tone="danger"
            title="We couldn't download the PDF"
            onDismiss={receipts.clearFailure}
          >
            Try again in a moment.
          </Alert>
        ) : null}
      </div>

      {pendingRevision ? (
        <Alert tone="warning" title="Confirm the new fee">
          <div className="flex flex-col gap-3">
            <p>
              The courier measured your parcel at pickup. The fee changes from{' '}
              <MoneyAmount amount={order.estimatedFee ?? 0} size="sm" inheritColor /> to{' '}
              <MoneyAmount amount={order.revisedFee ?? 0} size="sm" inheritColor />.
              {order.feeRevisionReason ? ` Courier's note: “${order.feeRevisionReason}”` : ''}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button
                type="button"
                loading={acceptMutation.isPending}
                loadingLabel="Accepting the new fee"
                disabled={revisionBusy}
                onClick={() => acceptMutation.mutate()}
              >
                Accept new fee
              </Button>
              <Button
                type="button"
                variant="secondary"
                disabled={revisionBusy}
                onClick={() => openDialog(setDeclineOpen)}
              >
                Decline
              </Button>
            </div>
          </div>
        </Alert>
      ) : null}

      <OrderStatusSection ref={statusRef} order={order} history={historyQuery.data} />

      {status !== 'CANCELLED' ? <OrderFeeSection order={order} /> : null}

      {canCancel(order) ? (
        <SurfaceCard>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-body text-secondary">
              Plans changed? You can cancel until the courier picks up the parcel.
            </p>
            <Button type="button" variant="outline" onClick={() => openDialog(setCancelOpen)}>
              Cancel parcel
            </Button>
          </div>
        </SurfaceCard>
      ) : null}

      <OrderDetailsSection order={order} />

      <CancelDialog
        open={cancelOpen}
        trackingNumber={trackingNumber}
        pending={cancelMutation.isPending}
        onClose={() => dismissDialog(setCancelOpen)}
        onConfirm={(reason) => cancelMutation.mutate(reason)}
      />
      <DeclineFeeDialog
        open={declineOpen}
        pending={declineMutation.isPending}
        bookedFee={order.estimatedFee ?? 0}
        revisedFee={order.revisedFee ?? 0}
        currency={order.currency ?? 'LKR'}
        onClose={() => dismissDialog(setDeclineOpen)}
        onConfirm={() => declineMutation.mutate()}
      />
    </div>
  );
}

export default OrderDetailPage;
