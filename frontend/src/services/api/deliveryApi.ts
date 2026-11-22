import { apiClient } from './apiClient';
import type { components } from '@/types/generated/delivery-execution-service';

export type DeliverySessionResponse = components['schemas']['DeliverySessionResponse'];
export type CourierJobResponse = components['schemas']['CourierJobResponse'];
export type CourierJobStop = components['schemas']['CourierJobStop'];
export type ShopStopResponse = components['schemas']['ShopStopResponse'];
export type ShopStopAddress = components['schemas']['ShopStopAddress'];
export type CollectShopRequest = components['schemas']['CollectShopRequest'];
export type CollectShopResponse = components['schemas']['CollectShopResponse'];
export type TransitionDeliveryRequest = components['schemas']['TransitionDeliveryRequest'];
export type ProofOfDeliveryUploadRequest = components['schemas']['ProofOfDeliveryUploadRequest'];
export type ProofOfDeliveryResponse = components['schemas']['ProofOfDeliveryResponse'];
export type DeliveryState = components['schemas']['DeliveryState'];
export type DeliveryFailureReason = components['schemas']['DeliveryFailureReason'];

export const deliveryApi = {
  getDeliveryById: async (deliveryId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DeliverySessionResponse }>(
      `/v1/deliveries/${deliveryId}`
    );
    return data.data;
  },

  /** Addresses, contacts and cash for the leg this courier carries. */
  getJob: async (deliveryId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: CourierJobResponse }>(
      `/v1/deliveries/${deliveryId}/job`
    );
    return data.data;
  },

  /** A shop's parcels this courier collects in one visit. */
  getShopStop: async (merchantId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: ShopStopResponse }>(
      `/v1/deliveries/shops/${merchantId}`
    );
    return data.data;
  },

  collectShop: async (merchantId: string, request: CollectShopRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: CollectShopResponse }>(
      `/v1/deliveries/shops/${merchantId}/collect`,
      request
    );
    return data.data;
  },

  getDeliveryByOrderId: async (orderId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DeliverySessionResponse }>(
      `/v1/deliveries/order/${orderId}`
    );
    return data.data;
  },

  getDeliveryByTrackingNumber: async (trackingNumber: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DeliverySessionResponse }>(
      `/v1/deliveries/tracking/${trackingNumber}`
    );
    return data.data;
  },

  getDeliveriesByCourier: async (courierId: string, state?: DeliveryState) => {
    const { data } = await apiClient.get<{ success: boolean; data: DeliverySessionResponse[] }>(
      `/v1/deliveries/courier/${courierId}`,
      { params: { state } }
    );
    return data.data;
  },

  getMyDeliveries: async (date?: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DeliverySessionResponse[] }>(
      '/v1/deliveries/me',
      { params: date ? { date } : undefined }
    );
    return data.data;
  },

  transitionDelivery: async (deliveryId: string, request: TransitionDeliveryRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: DeliverySessionResponse }>(
      `/v1/deliveries/${deliveryId}/transition`,
      request
    );
    return data.data;
  },

  uploadProofOfDelivery: async (deliveryId: string, request: ProofOfDeliveryUploadRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: ProofOfDeliveryResponse }>(
      `/v1/deliveries/${deliveryId}/proof-of-delivery`,
      request
    );
    return data.data;
  },

  uploadProofOfDeliveryMultipart: async (
    deliveryId: string,
    photo: File | Blob,
    recipientName: string,
    signature?: string
  ) => {
    const formData = new FormData();
    formData.append('photo', photo);
    formData.append('recipientName', recipientName);
    if (signature) formData.append('signature', signature);

    const { data } = await apiClient.post<{ success: boolean; data: ProofOfDeliveryResponse }>(
      `/v1/deliveries/${deliveryId}/proof-of-delivery`,
      formData
    );
    return data.data;
  },

  getProofOfDelivery: async (deliveryId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: ProofOfDeliveryResponse }>(
      `/v1/deliveries/${deliveryId}/proof-of-delivery`
    );
    return data.data;
  }
};
