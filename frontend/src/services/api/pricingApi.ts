import { isAxiosError } from 'axios';
import { apiClient } from './apiClient';
import type { components } from '@/types/generated/pricing-zone-service';

export type EstimateRequest = components['schemas']['EstimateRequest'];
export type EstimateResponse = components['schemas']['EstimateResponse'];
export type ZoneRequest = components['schemas']['ZoneRequest'];
export type ZoneResponse = components['schemas']['ZoneResponse'];
export type DistrictMappingRequest = components['schemas']['DistrictMappingRequest'];
export type DistrictMappingResponse = components['schemas']['DistrictMappingResponse'];
export type TariffRuleRequest = components['schemas']['TariffRuleRequest'];
export type TariffRuleResponse = components['schemas']['TariffRuleResponse'];
export type SriLankanDistrict = components['schemas']['SriLankanDistrict'];
export type OrderSpeed = components['schemas']['OrderSpeed'];

export const pricingApi = {
  /** Open to every signed-in role; used by the booking wizard preview. */
  estimate: async (request: EstimateRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: EstimateResponse }>(
      '/v1/pricing/estimate',
      request
    );
    return data.data;
  },

  listZones: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: ZoneResponse[] }>('/v1/zones');
    return data.data ?? [];
  },

  getZone: async (zoneCode: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: ZoneResponse }>(
      `/v1/zones/${encodeURIComponent(zoneCode)}`
    );
    return data.data;
  },

  /** Dispatcher or system admin only. */
  createZone: async (request: ZoneRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: ZoneResponse }>(
      '/v1/zones',
      request
    );
    return data.data;
  },

  /** Dispatcher or system admin only. The zone code cannot be changed. */
  updateZone: async (zoneCode: string, request: ZoneRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: ZoneResponse }>(
      `/v1/zones/${encodeURIComponent(zoneCode)}`,
      request
    );
    return data.data;
  },

  /** Dispatcher or system admin only. Fails with 409 while districts are mapped to the zone. */
  deleteZone: async (zoneCode: string) => {
    await apiClient.delete(`/v1/zones/${encodeURIComponent(zoneCode)}`);
  },

  listDistrictMappings: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: DistrictMappingResponse[] }>(
      '/v1/zones/districts'
    );
    return data.data ?? [];
  },

  /** Dispatcher or system admin only. */
  assignDistrict: async (district: SriLankanDistrict, request: DistrictMappingRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: DistrictMappingResponse }>(
      `/v1/zones/districts/${district}`,
      request
    );
    return data.data;
  },

  /** All tariff versions, newest first. */
  listTariffs: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: TariffRuleResponse[] }>(
      '/v1/pricing/tariffs'
    );
    return data.data ?? [];
  },

  getActiveTariff: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: TariffRuleResponse }>(
      '/v1/pricing/tariffs/active'
    );
    return data.data;
  },

  /** Dispatcher or system admin only. Creates a new version and deactivates the previous one. */
  publishTariff: async (request: TariffRuleRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: TariffRuleResponse }>(
      '/v1/pricing/tariffs/default',
      request
    );
    return data.data;
  }
};

/** Status and server message of a failed pricing call. */
export interface PricingApiError {
  status?: number;
  message?: string;
}

export function readPricingApiError(error: unknown): PricingApiError {
  if (!isAxiosError(error)) return {};
  const data = error.response?.data as { message?: unknown } | undefined;
  return {
    status: error.response?.status,
    message: typeof data?.message === 'string' ? data.message : undefined
  };
}