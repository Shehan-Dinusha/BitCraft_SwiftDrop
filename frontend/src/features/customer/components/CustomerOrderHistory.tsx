import { useState, useEffect } from 'react';
import {
  orderApi,
  type CustomerOrderResponse
} from '@/services/api/orderApi';
import {
  Button,
  SurfaceCard,
  FsmStatusBadge,
  StatusBanner,
  Modal,
  Field,
  Input
} from '@/design-system';
import {
  Package,
  FileDown,
  XCircle,
  AlertTriangle,
  CheckCircle2,
  Receipt,
  RefreshCw
} from 'lucide-react';
import { useAuthStore } from '@/store/useAuthStore';

export function CustomerOrderHistory() {
  const [orders, setOrders] = useState<CustomerOrderResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Cancellation modal state
  const [cancelModalOrder, setCancelModalOrder] = useState<CustomerOrderResponse | null>(null);
  const [cancelReason, setCancelReason] = useState('Change of plan');
  const [isCancelling, setIsCancelling] = useState(false);

  // Fee revision action state
  const [revisionActionLoading, setRevisionActionLoading] = useState<string | null>(null);

  const user = useAuthStore((state) => state.user);

  const fetchOrders = async () => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      const paged = await orderApi.listOrders(0, 50, user?.id || undefined);
      setOrders(paged.content || []);
    } catch (err: unknown) {
      const error = err as { message?: string };
      setErrorMsg(error.message || 'Failed to load order history');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void fetchOrders();
  }, [user?.id]);

  const handleAcceptRevision = async (orderId: string) => {
    setRevisionActionLoading(orderId);
    try {
      await orderApi.acceptFeeRevision(orderId);
      await fetchOrders();
    } catch (err: unknown) {
      const error = err as { message?: string };
      setErrorMsg(error.message || 'Failed to accept fee revision');
    } finally {
      setRevisionActionLoading(null);
    }
  };

  const handleRejectRevision = async (orderId: string) => {
    setRevisionActionLoading(orderId);
    try {
      await orderApi.rejectFeeRevision(orderId);
      await fetchOrders();
    } catch (err: unknown) {
      const error = err as { message?: string };
      setErrorMsg(error.message || 'Failed to reject fee revision');
    } finally {
      setRevisionActionLoading(null);
    }
  };

  const handleConfirmCancel = async () => {
    if (!cancelModalOrder?.orderId) return;
    setIsCancelling(true);
    try {
      await orderApi.cancelOrder(cancelModalOrder.orderId, cancelReason);
      setCancelModalOrder(null);
      await fetchOrders();
    } catch (err: unknown) {
      const error = err as { message?: string };
      setErrorMsg(error.message || 'Failed to cancel order');
    } finally {
      setIsCancelling(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-primary">Your Delivery Orders</h2>
          <p className="text-xs text-secondary">
            Manage your booked parcels, approve courier weight adjustments, and download official waybills.
          </p>
        </div>
        <Button
          variant="secondary"
          className="text-xs"
          leftIcon={<RefreshCw className={`size-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
          onClick={fetchOrders}
        >
          Refresh
        </Button>
      </div>

      {errorMsg && (
        <StatusBanner
          tone="danger"
          title="Notice"
        >
          {errorMsg}
        </StatusBanner>
      )}

      {isLoading ? (
        <div className="py-12 text-center text-sm text-secondary">Loading your orders...</div>
      ) : orders.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border-subtle p-12 text-center">
          <Package className="mx-auto size-12 text-muted mb-3" />
          <h3 className="text-base font-semibold text-primary">No orders placed yet</h3>
          <p className="text-xs text-secondary mt-1">Book your first parcel delivery using the booking tab.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {orders.map((order) => {
            const isTerminal = order.status === 'DELIVERED' || order.status === 'CANCELLED' || order.status === 'RETURNED_TO_SENDER';
            const hasPendingRevision = order.feeRevisionStatus === 'PENDING';

            return (
              <SurfaceCard key={order.orderId} className="border-border-subtle p-5 rounded-xl space-y-4">
                {/* Header: Tracking number & Status badge */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle pb-3">
                  <div className="flex items-center gap-3">
                    <span className="font-mono text-base font-black text-brand-primary tracking-wide">
                      {order.trackingNumber}
                    </span>
                    <FsmStatusBadge state={order.status as any} />
                    <span className="rounded bg-elevated px-2 py-0.5 text-[10px] font-bold text-secondary uppercase">
                      {order.orderSpeed}
                    </span>
                  </div>

                  <div className="text-right">
                    <span className="text-xs text-secondary">Delivery Fee:</span>
                    <span className="ml-2 font-bold text-primary">
                      LKR {order.finalFee?.toFixed(2) || order.estimatedFee?.toFixed(2)}
                    </span>
                  </div>
                </div>

                {/* ROUTE & METRICS */}
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 text-xs">
                  <div>
                    <span className="text-secondary block">Origin:</span>
                    <span className="font-semibold text-primary">
                      {order.pickupCity}, {order.pickupDistrict}
                    </span>
                    <span className="text-muted block text-[11px] truncate">
                      {order.pickupContactName} ({order.pickupContactPhone})
                    </span>
                  </div>

                  <div>
                    <span className="text-secondary block">Destination:</span>
                    <span className="font-semibold text-primary">
                      {order.deliveryCity}, {order.deliveryDistrict}
                    </span>
                    <span className="text-muted block text-[11px] truncate">
                      {order.recipientName} ({order.recipientPhone})
                    </span>
                  </div>

                  <div>
                    <span className="text-secondary block">Specifications:</span>
                    <span className="text-primary font-medium">
                      {order.actualWeightKg} kg (Billable: {order.chargeableWeightKg} kg)
                    </span>
                    <span className="text-muted block text-[11px]">
                      {order.lengthCm}x{order.widthCm}x{order.heightCm} cm
                      {order.codAmount ? ` • COD: LKR ${order.codAmount}` : ''}
                    </span>
                  </div>
                </div>

                {/* COURIER FEE REVISION BANNER (FR-C8, FR-K5) */}
                {hasPendingRevision && (
                  <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 space-y-3">
                    <div className="flex items-start gap-2.5">
                      <AlertTriangle className="size-5 text-amber-500 shrink-0 mt-0.5" />
                      <div>
                        <h4 className="text-xs font-bold text-amber-600 dark:text-amber-400 uppercase tracking-wider">
                          Courier Pickup Fee Revision Pending Your Approval (FR-C8)
                        </h4>
                        <p className="text-xs text-primary mt-0.5">
                          The courier has revised the delivery fee to{' '}
                          <span className="font-bold text-amber-600 dark:text-amber-400">
                            LKR {order.revisedFee?.toFixed(2)}
                          </span>{' '}
                          (Original: LKR {order.estimatedFee?.toFixed(2)}).
                        </p>
                        {order.feeRevisionReason && (
                          <p className="text-[11px] text-secondary mt-1 italic">
                            Reason: "{order.feeRevisionReason}"
                          </p>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-3 pt-1">
                      <Button
                        variant="primary"
                        className="text-xs bg-emerald-600 hover:bg-emerald-700"
                        disabled={revisionActionLoading === order.orderId}
                        leftIcon={<CheckCircle2 className="size-3.5" />}
                        onClick={() => handleAcceptRevision(order.orderId as string)}
                      >
                        Accept New Fee (LKR {order.revisedFee})
                      </Button>

                      <Button
                        variant="secondary"
                        className="text-xs text-red-600 hover:bg-red-500/10"
                        disabled={revisionActionLoading === order.orderId}
                        leftIcon={<XCircle className="size-3.5" />}
                        onClick={() => handleRejectRevision(order.orderId as string)}
                      >
                        Reject & Auto-Cancel ($0)
                      </Button>
                    </div>
                  </div>
                )}

                {/* ACTION BUTTONS & PDF DOWNLOADS (FR-C4, FR-C5, FR-C7) */}
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border-subtle pt-3">
                  <div className="flex flex-wrap items-center gap-2">
                    {/* A5 Shipping Label & Fee Estimate Receipt PDF */}
                    <a
                      href={orderApi.getShippingLabelUrl(order.orderId as string)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 rounded-lg border border-border-subtle bg-elevated px-3 py-1.5 text-xs font-medium text-primary hover:bg-brand-subtle hover:text-brand-primary transition-all"
                    >
                      <FileDown className="size-3.5" />
                      Shipping Label (A5 PDF)
                    </a>

                    {/* Delivery & Cash Receipt PDF (available anytime, finalized on delivery) */}
                    {order.status === 'DELIVERED' && (
                      <a
                        href={orderApi.getDeliveryReceiptUrl(order.orderId as string)}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/20 transition-all"
                      >
                        <Receipt className="size-3.5" />
                        Final Delivery Receipt
                      </a>
                    )}
                  </div>

                  {!isTerminal && (
                    <Button
                      variant="ghost"
                      className="text-xs text-secondary hover:text-red-500"
                      leftIcon={<XCircle className="size-3.5" />}
                      onClick={() => setCancelModalOrder(order)}
                    >
                      Cancel Order
                    </Button>
                  )}
                </div>
              </SurfaceCard>
            );
          })}
        </div>
      )}

      {/* CANCELLATION MODAL (FR-C3) */}
      {cancelModalOrder && (
        <Modal
          open={true}
          onClose={() => setCancelModalOrder(null)}
          title={`Cancel Order ${cancelModalOrder.trackingNumber}`}
        >
          <div className="space-y-4">
            <p className="text-xs text-secondary">
              Are you sure you want to cancel this order? If already dispatched or assigned, cancellation will be recorded and published to the courier network.
            </p>

            <Field label="Reason for Cancellation" required>
              <Input
                placeholder="e.g. Sender changed plans, incorrect address"
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
              />
            </Field>

            <div className="flex justify-end gap-3 pt-2">
              <Button
                variant="secondary"
                disabled={isCancelling}
                onClick={() => setCancelModalOrder(null)}
              >
                Keep Order
              </Button>
              <Button
                variant="primary"
                className="bg-red-600 hover:bg-red-700"
                disabled={isCancelling || !cancelReason.trim()}
                onClick={handleConfirmCancel}
              >
                {isCancelling ? 'Cancelling...' : 'Confirm Cancellation'}
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
