import { apiClient } from './apiClient';
import type { components } from '@/types/generated/delivery-execution-service';

type Schemas = components['schemas'];
export type CourierCashResponse = Schemas['CourierCashResponse'];
export type CashEntryResponse = Schemas['CashEntryResponse'];
export type CashHandoverResponse = Schemas['CashHandoverResponse'];
export type CashDepositResponse = Schemas['CashDepositResponse'];
export type CashHoldingResponse = Schemas['CashHoldingResponse'];
export type CashDeskResponse = Schemas['CashDeskResponse'];
export type CashReportResponse = Schemas['CashReportResponse'];
export type SriLankanDistrict = Schemas['SriLankanDistrict'];

type Envelope<T> = { success: boolean; data: T };

const get = async <T>(url: string, params?: Record<string, string>) =>
  (await apiClient.get<Envelope<T>>(url, { params })).data.data;
const post = async <T>(url: string, body?: unknown) =>
  (await apiClient.post<Envelope<T>>(url, body)).data.data;

export const cashApi = {
  mine: () => get<CourierCashResponse>('/v1/cash/me'),
  submitHandover: () => post<CashHandoverResponse>('/v1/cash/handovers'),
  desk: (district?: SriLankanDistrict) =>
    get<CashDeskResponse>('/v1/cash/desk', district ? { district } : undefined),
  handoverEntries: (handoverId: string) =>
    get<CashEntryResponse[]>(`/v1/cash/handovers/${handoverId}/entries`),
  report: (from: string, to: string, district?: SriLankanDistrict) =>
    get<CashReportResponse>('/v1/cash/report', district ? { from, to, district } : { from, to }),
  count: (handoverId: string, body: Schemas['CountHandoverRequest']) =>
    post<CashHandoverResponse>(`/v1/cash/handovers/${handoverId}/count`, body),
  deposit: (body: Schemas['CreateDepositRequest']) =>
    post<CashDepositResponse>('/v1/cash/deposits', body),
  confirmDeposit: (depositId: string) =>
    post<CashDepositResponse>(`/v1/cash/deposits/${depositId}/confirm`),
  rejectDeposit: (depositId: string, reason: string) =>
    post<CashDepositResponse>(`/v1/cash/deposits/${depositId}/reject`, { reason }),
  refund: (trackingNumber: string, reason: string) =>
    post<CashEntryResponse>('/v1/cash/refunds', { trackingNumber, reason })
};
