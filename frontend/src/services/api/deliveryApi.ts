import { apiClient } from './apiClient';
import type { components } from '@/types/generated/delivery-execution-service';

export type DeliverySessionResponse = components['schemas']['DeliverySessionResponse'];
export type TransitionDeliveryRequest = components['schemas']['TransitionDeliveryRequest'];
export type CourierLocationUpdatePayload = components['schemas']['CourierLocationUpdatePayload'];
export type TrackingBreadcrumbResponse = components['schemas']['TrackingBreadcrumbResponse'];
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

  transitionDelivery: async (deliveryId: string, request: TransitionDeliveryRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: DeliverySessionResponse }>(
      `/v1/deliveries/${deliveryId}/transition`,
      request
    );
    return data.data;
  },

  recordLocation: async (deliveryId: string, payload: CourierLocationUpdatePayload) => {
    const { data } = await apiClient.post<{ success: boolean; data: TrackingBreadcrumbResponse }>(
      `/v1/deliveries/${deliveryId}/location`,
      payload
    );
    return data.data;
  },

  getBreadcrumbs: async (deliveryId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: TrackingBreadcrumbResponse[] }>(
      `/v1/deliveries/${deliveryId}/breadcrumbs`
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

  verifyProofOfDelivery: async (deliveryId: string, otp: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: ProofOfDeliveryResponse }>(
      `/v1/deliveries/${deliveryId}/proof-of-delivery/verify`,
      null,
      { params: { otp } }
    );
    return data.data;
  },

  getProofOfDelivery: async (deliveryId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: ProofOfDeliveryResponse }>(
      `/v1/deliveries/${deliveryId}/proof-of-delivery`
    );
    return data.data;
  },
};
