import { apiClient } from './apiClient';
import type { components } from '@/types/generated/webhook-resiliency-service';

export type CreateWebhookEndpointRequest = components['schemas']['CreateWebhookEndpointRequest'];
export type WebhookEndpointResponse = components['schemas']['WebhookEndpointResponse'];
/** Create-only response: the endpoint plus its signing secret, which the API returns this one time only. */
export type WebhookEndpointCreatedResponse = components['schemas']['WebhookEndpointCreatedResponse'];
export type WebhookDeliveryAttemptResponse = components['schemas']['WebhookDeliveryAttemptResponse'];
export type FailedDeliveryResponse = components['schemas']['FailedDeliveryResponse'];

export const webhookApi = {
  /**
   * The backend scopes results to the caller's merchant (from the verified JWT). `merchantId` is an optional
   * filter for SYSTEM_ADMIN only; merchant screens must not send it.
   */
  listEndpoints: async (adminMerchantFilter?: string) => {
    const params = adminMerchantFilter ? { merchantId: adminMerchantFilter } : {};
    const { data } = await apiClient.get<{ success: boolean; data: WebhookEndpointResponse[] }>(
      '/v1/webhooks/subscriptions',
      { params }
    );
    return data.data;
  },

  createEndpoint: async (request: CreateWebhookEndpointRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: WebhookEndpointCreatedResponse }>(
      '/v1/webhooks/subscriptions',
      request
    );
    return data.data;
  },

  getEndpoint: async (endpointId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: WebhookEndpointResponse }>(
      `/v1/webhooks/subscriptions/${endpointId}`
    );
    return data.data;
  },

  toggleEndpointActive: async (endpointId: string, active: boolean) => {
    const { data } = await apiClient.patch<{ success: boolean; data: WebhookEndpointResponse }>(
      `/v1/webhooks/subscriptions/${endpointId}/status`,
      null,
      { params: { active } }
    );
    return data.data;
  },

  deleteEndpoint: async (endpointId: string) => {
    const { data } = await apiClient.delete<{ success: boolean; message: string }>(
      `/v1/webhooks/subscriptions/${endpointId}`
    );
    return data;
  },

  testPing: async (endpointId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: WebhookDeliveryAttemptResponse }>(
      `/v1/webhooks/subscriptions/${endpointId}/test`
    );
    return data.data;
  },

  listAttempts: async (endpointId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: WebhookDeliveryAttemptResponse[] }>(
      `/v1/webhooks/subscriptions/${endpointId}/attempts`
    );
    return data.data;
  },

  /** Deliveries that used up their attempts, newest first. */
  listFailedDeliveries: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: FailedDeliveryResponse[] }>(
      '/v1/webhooks/deliveries/failed'
    );
    return data.data;
  },

  /** Queues it to be sent again in the background. Safe to repeat. */
  replayFailedDelivery: async (deliveryId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: FailedDeliveryResponse }>(
      `/v1/webhooks/deliveries/failed/${deliveryId}/replay`
    );
    return data.data;
  },

  /** Queues every one not yet replayed, and returns how many. */
  replayAllFailedDeliveries: async () => {
    const { data } = await apiClient.post<{ success: boolean; data: number }>(
      '/v1/webhooks/deliveries/failed/replay'
    );
    return data.data;
  }
};
