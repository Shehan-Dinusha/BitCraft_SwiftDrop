import { useCallback, useState } from 'react';
import { orderApi, readOrderApiError, type CustomerOrderResponse } from '@/services/api/orderApi';

export type ReceiptKind = 'receipt' | 'delivery-receipt';

/**
 * Tracks which order's PDF is downloading so each button shows its own spinner. Failures keep only
 * the HTTP status; nothing from the order is logged.
 */
export function useReceiptDownload() {
  const [pending, setPending] = useState<string | null>(null);
  const [failed, setFailed] = useState<{ key: string; status?: number } | null>(null);

  const download = useCallback(
    async (order: CustomerOrderResponse, kind: ReceiptKind = 'receipt') => {
      if (!order.orderId) return;
      const key = `${order.orderId}:${kind}`;
      setPending(key);
      setFailed(null);
      try {
        if (kind === 'delivery-receipt') {
          await orderApi.downloadDeliveryReceipt(order.orderId, order.trackingNumber);
        } else {
          await orderApi.downloadReceipt(order.orderId, order.trackingNumber);
        }
      } catch (error) {
        const { status } = readOrderApiError(error);
        console.warn('Receipt download failed', status);
        setFailed({ key, status });
      } finally {
        setPending(null);
      }
    },
    []
  );

  return {
    download,
    isPending: (orderId: string | undefined, kind: ReceiptKind = 'receipt') =>
      pending === `${orderId}:${kind}`,
    failure: failed,
    clearFailure: () => setFailed(null)
  };
}
