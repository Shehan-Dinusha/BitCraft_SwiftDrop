import { apiClient } from './apiClient';
import type { components } from '@/types/generated/customer-order-service';

export type CreateOrderRequest = components['schemas']['CreateOrderRequest'];
export type UpdateOrderRequest = components['schemas']['UpdateOrderRequest'];
export type FeeEstimateRequest = components['schemas']['FeeEstimateRequest'];
export type FeeEstimateResponse = components['schemas']['FeeEstimateResponse'];
export type CustomerOrderResponse = components['schemas']['CustomerOrderResponse'];
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
  getDeliveryReceiptUrl: (orderId: string) => `/api/v1/orders/${orderId}/delivery-receipt`
};
