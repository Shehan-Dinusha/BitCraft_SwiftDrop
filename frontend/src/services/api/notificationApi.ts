import { apiClient } from './apiClient';
import type { components } from '@/types/generated/notification-service';

export type RecipientPreferenceResponse = components['schemas']['RecipientPreferenceResponse'];
export type UpdatePreferenceRequest = components['schemas']['UpdatePreferenceRequest'];
export type NotificationDispatchLogResponse = components['schemas']['NotificationDispatchLogResponse'];
export type DispatchNotificationRequest = components['schemas']['DispatchNotificationRequest'];
export type InAppNotificationResponse = components['schemas']['InAppNotificationResponse'];
export type PushPublicKeyResponse = components['schemas']['PushPublicKeyResponse'];
export type PushSubscriptionRequest = components['schemas']['PushSubscriptionRequest'];

export const notificationApi = {
  getCurrentPreferences: async (recipientId?: string) => {
    const params = recipientId ? { recipientId } : {};
    const { data } = await apiClient.get<{ success: boolean; data: RecipientPreferenceResponse }>(
      '/v1/notifications/preferences',
      { params }
    );
    return data.data;
  },

  getPreferences: async (recipientId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: RecipientPreferenceResponse }>(
      `/v1/notifications/preferences/${recipientId}`
    );
    return data.data;
  },

  updatePreferences: async (recipientId: string, request: UpdatePreferenceRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: RecipientPreferenceResponse }>(
      `/v1/notifications/preferences/${recipientId}`,
      request
    );
    return data.data;
  },

  listLogs: async (params?: { recipientId?: string; channel?: string; status?: string }) => {
    const { data } = await apiClient.get<{ success: boolean; data: NotificationDispatchLogResponse[] }>(
      '/v1/notifications/logs',
      { params }
    );
    return data.data;
  },

  getLog: async (logId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: NotificationDispatchLogResponse }>(
      `/v1/notifications/logs/${logId}`
    );
    return data.data;
  },

  dispatch: async (request: DispatchNotificationRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: NotificationDispatchLogResponse }>(
      '/v1/notifications/logs/dispatch',
      request
    );
    return data.data;
  },

  getUnreadCount: async (): Promise<number> => {
    const { data } = await apiClient.get<{ success?: boolean; data?: number }>(
      '/v1/notifications/me/unread-count'
    );
    return data.data ?? 0;
  },

  listMyNotifications: async (): Promise<InAppNotificationResponse[]> => {
    const { data } = await apiClient.get<{ success?: boolean; data?: InAppNotificationResponse[] }>(
      '/v1/notifications/me'
    );
    return data.data ?? [];
  },

  markAsRead: async (id: number): Promise<InAppNotificationResponse> => {
    const { data } = await apiClient.patch<{ success?: boolean; data?: InAppNotificationResponse }>(
      `/v1/notifications/me/${id}/read`
    );
    return data.data as InAppNotificationResponse;
  },

  markAllAsRead: async (): Promise<void> => {
    await apiClient.post('/v1/notifications/me/read-all');
  },

  /** VAPID key for pushManager.subscribe; enabled is false when the server has no keys. */
  getPushPublicKey: async (): Promise<PushPublicKeyResponse> => {
    const { data } = await apiClient.get<{ success?: boolean; data?: PushPublicKeyResponse }>(
      '/v1/notifications/push/public-key'
    );
    return data.data ?? { enabled: false, publicKey: null };
  },

  /** Registers this device (the browser's PushSubscription.toJSON()). */
  registerPushSubscription: async (subscription: PushSubscriptionRequest): Promise<void> => {
    await apiClient.post('/v1/notifications/me/push-subscriptions', subscription);
  },

  /** Stops notifications on this device; the server only removes it when it belongs to the caller. */
  removePushSubscription: async (endpoint: string): Promise<void> => {
    await apiClient.delete('/v1/notifications/me/push-subscriptions', { data: { endpoint } });
  }
};
