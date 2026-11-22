import { apiClient } from './apiClient';
import type { SriLankanDistrict } from './dispatchApi';
import type { components } from '@/types/generated/analytics-ai-service';

export type CourierPerformance = components['schemas']['CourierPerformanceResponse'];
export type AiChatRequest = components['schemas']['AiChatRequest'];
export type AiChatResponse = components['schemas']['AiChatResponse'];

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

/**
 * `district` is for a system admin: one district, or undefined for the whole island. The server ignores it
 * for a dispatcher, who always gets their own.
 */
export const analyticsAiApi = {
  getDailyDeliveries: async (
    from: string,
    to: string,
    district?: SriLankanDistrict,
    signal?: AbortSignal
  ) => {
    const res = await apiClient.get<components['schemas']['ApiResponseDailyDeliveryReport']>(
      '/v1/analytics/deliveries/daily',
      { params: { from, to, district }, signal }
    );
    return res.data.data;
  },
  getSuccessRate: async (
    from: string,
    to: string,
    district?: SriLankanDistrict,
    signal?: AbortSignal
  ) => {
    const res = await apiClient.get<components['schemas']['ApiResponseDeliverySuccessRate']>(
      '/v1/analytics/success-rate',
      { params: { from, to, district }, signal }
    );
    return res.data.data;
  },
  getCourierWorkload: async (district?: SriLankanDistrict, signal?: AbortSignal) => {
    const res = await apiClient.get<components['schemas']['ApiResponseCourierWorkloadReport']>(
      '/v1/analytics/couriers/workload',
      { params: { district }, signal }
    );
    return res.data.data;
  },
  chat: async (payload: AiChatRequest, signal?: AbortSignal): Promise<AiChatResponse> => {
    const res = await apiClient.post<components['schemas']['ApiResponseAiChat']>(
      '/v1/ai/chat',
      payload,
      { signal }
    );
    return res.data.data;
  },
  getCourierPerformances: async (district?: SriLankanDistrict): Promise<CourierPerformance[]> => {
    const res = await apiClient.get<ApiResponse<CourierPerformance[]>>('/v1/analytics/couriers', {
      params: district ? { district } : undefined
    });
    return res.data.data;
  }
};
