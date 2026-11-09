import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type { components } from '@/types/generated/customer-order-service';

export type CreateOrderRequest = components['schemas']['CreateOrderRequest'];
export type UpdateOrderRequest = components['schemas']['UpdateOrderRequest'];
export type FeeEstimateRequest = components['schemas']['FeeEstimateRequest'];
export type FeeEstimateResponse = components['schemas']['FeeEstimateResponse'];
export type CustomerOrderResponse = components['schemas']['CustomerOrderResponse'];
export type OrderStatusHistoryEntry = components['schemas']['OrderStatusHistoryEntry'];
export type CancelOrderRequest = components['schemas']['CancelOrderRequest'];
export type SriLankanDistrict = components['schemas']['SriLankanDistrict'];
export type OrderSpeed = components['schemas']['OrderSpeed'];

export interface PagedOrderResult {
  content: CustomerOrderResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const orderApi = {
  estimateFee: async (request: FeeEstimateRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: FeeEstimateResponse }>(
      '/v1/orders/estimate',
      request
    );
    return data.data;
  },

  createOrder: async (request: CreateOrderRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: CustomerOrderResponse }>(
      '/v1/orders',
      request
    );
    return data.data;
  },

  getOrderById: async (orderId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: CustomerOrderResponse }>(
      `/v1/orders/${orderId}`
    );
    return data.data;
  },

  /** The statuses the order has been through, oldest first. */
  getStatusHistory: async (orderId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: OrderStatusHistoryEntry[] }>(
      `/v1/orders/${orderId}/status-history`
    );
    return data.data ?? [];
  },

  getOrderByTrackingNumber: async (trackingNumber: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: CustomerOrderResponse }>(
      `/v1/orders/tracking/${trackingNumber}`
    );
    return data.data;
  },

  listOrders: async (page = 0, size = 10, customerId?: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: PagedOrderResult }>(
      '/v1/orders',
      {
        params: { page, size, ...(customerId ? { customerId } : {}) }
      }
    );
    return data.data;
  },

  updateOrder: async (orderId: string, request: UpdateOrderRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: CustomerOrderResponse }>(
      `/v1/orders/${orderId}`,
      request
    );
    return data.data;
  },

  deleteOrder: async (orderId: string) => {
    const { data } = await apiClient.delete<{ success: boolean; message: string }>(
      `/v1/orders/${orderId}`
    );
    return data;
  },

  cancelOrder: async (orderId: string, reason: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: CustomerOrderResponse }>(
      `/v1/orders/${orderId}/cancel`,
      { reason }
    );
    return data.data;
  },

  acceptFeeRevision: async (orderId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: CustomerOrderResponse }>(
      `/v1/orders/${orderId}/fee-revision/accept`
    );
    return data.data;
  },

  rejectFeeRevision: async (orderId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: CustomerOrderResponse }>(
      `/v1/orders/${orderId}/fee-revision/reject`
    );
    return data.data;
  },

  getShippingLabelUrl: (orderId: string) => `/api/v1/orders/${orderId}/shipping-label`,
  getDeliveryReceiptUrl: (orderId: string) => `/api/v1/orders/${orderId}/delivery-receipt`,

  /** Customer receipt: the A5 shipping label plus fee receipt PDF. */
  downloadReceipt: (orderId: string, trackingNumber?: string) =>
    downloadPdf(`/v1/orders/${orderId}/shipping-label`, 'receipt', trackingNumber ?? orderId),

  /** Final delivery and cash collection receipt, available once the parcel is delivered. */
  downloadDeliveryReceipt: (orderId: string, trackingNumber?: string) =>
    downloadPdf(
      `/v1/orders/${orderId}/delivery-receipt`,
      'delivery-receipt',
      trackingNumber ?? orderId
    )
};

/**
 * Fetches a PDF through `apiClient` so the bearer token is sent (a plain link would reach the
 * gateway without it), then saves it under the server's filename or `<prefix>-<id>.pdf`.
 */
async function downloadPdf(path: string, prefix: string, id: string) {
  const response = await apiClient.get<Blob>(path, { responseType: 'blob' });
  const fallback = `${prefix}-${id.replace(/[^A-Za-z0-9-]/g, '')}.pdf`;
  saveBlob(
    response.data,
    filenameFromDisposition(response.headers['content-disposition']) ?? fallback
  );
}

/** Status and server message of a failed order call. The message is the service's fixed text. */
export interface OrderApiError {
  status?: number;
  message?: string;
}

export function readOrderApiError(error: unknown): OrderApiError {
  if (!isAxiosError(error)) return {};
  const data = error.response?.data as { message?: unknown } | undefined;
  return {
    status: error.response?.status,
    message: typeof data?.message === 'string' ? data.message : undefined
  };
}

function filenameFromDisposition(header: unknown): string | undefined {
  if (typeof header !== 'string') return undefined;
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header);
  const name = match?.[1]?.trim();
  return name && /^[A-Za-z0-9._-]+$/.test(name) ? name : undefined;
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Give the browser a moment to start the download before the URL is released.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
