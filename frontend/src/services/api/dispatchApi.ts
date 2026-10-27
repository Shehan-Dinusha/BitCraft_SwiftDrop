import { apiClient } from './apiClient';
import type { components } from '@/types/generated/dispatch-assignment-service';

export type DispatchAssignmentResponse = components['schemas']['DispatchAssignmentResponse'];
export type CourierShiftResponse = components['schemas']['CourierShiftResponse'];
export type ManualAssignRequest = components['schemas']['ManualAssignRequest'];
export type StartShiftRequest = components['schemas']['StartShiftRequest'];
export type UpdateShiftStatusRequest = components['schemas']['UpdateShiftStatusRequest'];
export type CourierLocationPingRequest = components['schemas']['CourierLocationPingRequest'];
export type AssignmentStatus = components['schemas']['AssignmentStatus'];
export type ShiftStatus = components['schemas']['ShiftStatus'];
export type SriLankanDistrict = components['schemas']['SriLankanDistrict'];

export interface PagedDispatchResult {
  content: DispatchAssignmentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export const dispatchApi = {
  getAssignments: async (params?: { status?: AssignmentStatus; page?: number; size?: number }) => {
    const { data } = await apiClient.get<{ success: boolean; data: PagedDispatchResult }>(
      '/v1/dispatch/assignments',
      { params }
    );
    return data.data;
  },

  getAssignmentById: async (assignmentId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse }>(
      `/v1/dispatch/assignments/${assignmentId}`
    );
    return data.data;
  },

  getAssignmentByOrderId: async (orderId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse }>(
      `/v1/dispatch/assignments/order/${orderId}`
    );
    return data.data;
  },

  getUnassignedParcels: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse[] }>(
      '/v1/dispatch/unassigned'
    );
    return data.data;
  },

  manualAssign: async (request: ManualAssignRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: DispatchAssignmentResponse }>(
      '/v1/dispatch/assign',
      request
    );
    return data.data;
  },

  getCourierAssignments: async (courierId: string, status?: AssignmentStatus) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse[] }>(
      `/v1/dispatch/couriers/${courierId}/assignments`,
      { params: { status } }
    );
    return data.data;
  },

  getCouriers: async (params?: { district?: SriLankanDistrict; status?: ShiftStatus }) => {
    const { data } = await apiClient.get<{ success: boolean; data: CourierShiftResponse[] }>(
      '/v1/dispatch/couriers',
      { params }
    );
    return data.data;
  },

  getCourier: async (courierId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: CourierShiftResponse }>(
      `/v1/dispatch/couriers/${courierId}`
    );
    return data.data;
  },

  startShift: async (request: StartShiftRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: CourierShiftResponse }>(
      '/v1/dispatch/couriers/shifts/start',
      request
    );
    return data.data;
  },

  endShift: async (courierId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: CourierShiftResponse }>(
      `/v1/dispatch/couriers/shifts/${courierId}/end`
    );
    return data.data;
  },

  updateShiftStatus: async (request: UpdateShiftStatusRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: CourierShiftResponse }>(
      '/v1/dispatch/couriers/shifts/status',
      request
    );
    return data.data;
  },

  updateLocation: async (request: CourierLocationPingRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: CourierShiftResponse }>(
      '/v1/dispatch/couriers/shifts/location',
      request
    );
    return data.data;
  },
};
