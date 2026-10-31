import { apiClient } from './apiClient';
import type { components } from '@/types/generated/analytics-ai-service';

export type OperationalKpiSummary = components['schemas']['OperationalKpiSummaryResponse'];
export type DistrictKpi = components['schemas']['DistrictKpiResponse'];
export type CourierPerformance = components['schemas']['CourierPerformanceResponse'];
export type DelayRiskPredictionRequest = components['schemas']['DelayRiskPredictionRequest'];
export type DelayRiskPredictionResponse = components['schemas']['DelayRiskPredictionResponse'];
export type AnomalyRerouteRequest = components['schemas']['AnomalyRerouteRequest'];
export type AnomalyRerouteResponse = components['schemas']['AnomalyRerouteResponse'];
export type AssistantQueryRequest = components['schemas']['AssistantQueryRequest'];
export type AssistantQueryResponse = components['schemas']['AssistantQueryResponse'];
export type AiAudit = components['schemas']['AiAuditResponse'];

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp?: string;
}

export const analyticsAiApi = {
  getOperationalKpis: async (): Promise<OperationalKpiSummary> => {
    const res = await apiClient.get<ApiResponse<OperationalKpiSummary>>('/v1/analytics/kpis');
    return res.data.data;
  },

  getCourierPerformances: async (district?: string): Promise<CourierPerformance[]> => {
    const res = await apiClient.get<ApiResponse<CourierPerformance[]>>('/v1/analytics/couriers', {
      params: district ? { district } : undefined
    });
    return res.data.data;
  },

  getCourierPerformance: async (courierId: string): Promise<CourierPerformance> => {
    const res = await apiClient.get<ApiResponse<CourierPerformance>>(`/v1/analytics/couriers/${courierId}`);
    return res.data.data;
  },

  aggregateSnapshots: async (): Promise<OperationalKpiSummary> => {
    const res = await apiClient.post<ApiResponse<OperationalKpiSummary>>('/v1/analytics/snapshots/aggregate');
    return res.data.data;
  },

  predictDelayRisk: async (payload: DelayRiskPredictionRequest): Promise<DelayRiskPredictionResponse> => {
    const res = await apiClient.post<ApiResponse<DelayRiskPredictionResponse>>('/v1/analytics/ai/predict-delay', payload);
    return res.data.data;
  },

  suggestReroute: async (payload: AnomalyRerouteRequest): Promise<AnomalyRerouteResponse> => {
    const res = await apiClient.post<ApiResponse<AnomalyRerouteResponse>>('/v1/analytics/ai/reroute-suggestions', payload);
    return res.data.data;
  },

  queryAssistant: async (payload: AssistantQueryRequest): Promise<AssistantQueryResponse> => {
    const res = await apiClient.post<ApiResponse<AssistantQueryResponse>>('/v1/analytics/ai/agent-query', payload);
    return res.data.data;
  },

  getAiAudits: async (): Promise<AiAudit[]> => {
    const res = await apiClient.get<ApiResponse<AiAudit[]>>('/v1/analytics/ai/audits');
    return res.data.data;
  }
};
