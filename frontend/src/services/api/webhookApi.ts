import { apiClient } from './apiClient';
import type { components } from '@/types/generated/webhook-resiliency-service';

export type CreateWebhookEndpointRequest = components['schemas']['CreateWebhookEndpointRequest'];
export type WebhookEndpointResponse = components['schemas']['WebhookEndpointResponse'];
export type WebhookDeliveryAttemptResponse = components['schemas']['WebhookDeliveryAttemptResponse'];
export type DeadLetterWebhookResponse = components['schemas']['DeadLetterWebhookResponse'];
export type ReplayDlqResponse = components['schemas']['ReplayDlqResponse'];

export const webhookApi = {
  listEndpoints: async (merchantId?: string) => {
    const params = merchantId ? { merchantId } : {};
    const { data } = await apiClient.get<{ success: boolean; data: WebhookEndpointResponse[] }>(
      '/v1/webhooks/subscriptions',
      { params }
    );
    return data.data;
  },

  createEndpoint: async (request: CreateWebhookEndpointRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: WebhookEndpointResponse }>(
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

  listDlq: async (merchantId?: string, unreplayedOnly: boolean = false) => {
    const params: Record<string, string | boolean> = { unreplayedOnly };
    if (merchantId) {
      params.merchantId = merchantId;
    }
    const { data } = await apiClient.get<{ success: boolean; data: DeadLetterWebhookResponse[] }>(
      '/v1/webhooks/dlq',
      { params }
    );
    return data.data;
  },

  getDlqRecord: async (dlqId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DeadLetterWebhookResponse }>(
      `/v1/webhooks/dlq/${dlqId}`
    );
    return data.data;
  },

  replayDlq: async (dlqId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: ReplayDlqResponse }>(
      `/v1/webhooks/dlq/${dlqId}/replay`
    );
    return data.data;
  },

  replayAllDlq: async (merchantId?: string) => {
    const params = merchantId ? { merchantId } : {};
    const { data } = await apiClient.post<{ success: boolean; data: ReplayDlqResponse[] }>(
      '/v1/webhooks/dlq/replay-all',
      null,
      { params }
    );
    return data.data;
  },
};
