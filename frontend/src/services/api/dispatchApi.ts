import { apiClient } from './apiClient';
import type { components } from '@/types/generated/dispatch-assignment-service';

export type DispatchAssignmentResponse = components['schemas']['DispatchAssignmentResponse'];
export type CourierShiftResponse = components['schemas']['CourierShiftResponse'];
export type ManualAssignRequest = components['schemas']['ManualAssignRequest'];
export type ReassignCourierRequest = components['schemas']['ReassignCourierRequest'];
export type CourierWorkloadResponse = components['schemas']['CourierWorkloadResponse'];
export type CourierSuggestionResponse = components['schemas']['CourierSuggestionResponse'];
export type StartShiftRequest = components['schemas']['StartShiftRequest'];
export type UpdateShiftStatusRequest = components['schemas']['UpdateShiftStatusRequest'];
export type AssignmentStatus = components['schemas']['AssignmentStatus'];
export type ShiftStatus = components['schemas']['ShiftStatus'];
export type SriLankanDistrict = components['schemas']['SriLankanDistrict'];
export type VehicleType = components['schemas']['VehicleType'];
export type CourierProfileResponse = components['schemas']['CourierProfileResponse'];
export type CourierVehicleRequest = components['schemas']['CourierVehicleRequest'];
export type OrderCourierResponse = components['schemas']['OrderCourierResponse'];

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

  getCourierSuggestions: async (orderId: string) => {
    const { data } = await apiClient.get<{
      success: boolean;
      data: CourierSuggestionResponse[];
    }>(`/v1/dispatch/orders/${orderId}/suggestions`);
    return data.data;
  },

  manualAssign: async (request: ManualAssignRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: DispatchAssignmentResponse }>(
      '/v1/dispatch/assign',
      request
    );
    return data.data;
  },

  reassign: async (request: ReassignCourierRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: DispatchAssignmentResponse }>(
      '/v1/dispatch/reassign',
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

  getCourierWorkloads: async () => {
    const { data } = await apiClient.get<{
      success: boolean;
      data: CourierWorkloadResponse[];
    }>('/v1/dispatch/couriers/workload');
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

  /** The signed-in courier's registered vehicle, or null before they have registered one. */
  getMyProfile: async (): Promise<CourierProfileResponse | null> => {
    try {
      const { data } = await apiClient.get<{ success: boolean; data: CourierProfileResponse }>(
        '/v1/dispatch/couriers/me/profile'
      );
      return data.data;
    } catch (error) {
      if ((error as { response?: { status?: number } }).response?.status === 404) return null;
      throw error;
    }
  },

  /** The courier registers their own vehicle, once. */
  registerMyProfile: async (request: CourierVehicleRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: CourierProfileResponse }>(
      '/v1/dispatch/couriers/me/profile',
      request
    );
    return data.data;
  },

  /** Vehicles of the couriers a dispatcher manages (their own district). */
  getCourierProfiles: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: CourierProfileResponse[] }>(
      '/v1/dispatch/couriers/profiles'
    );
    return data.data ?? [];
  },

  /** A dispatcher corrects a courier's vehicle or capacity. */
  updateCourierProfile: async (courierId: string, request: CourierVehicleRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: CourierProfileResponse }>(
      `/v1/dispatch/couriers/${courierId}/profile`,
      request
    );
    return data.data;
  },

  /** The courier handling an order (first name and vehicle), or null while none is assigned. */
  getOrderCourier: async (orderId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: OrderCourierResponse | null }>(
      `/v1/dispatch/orders/${orderId}/courier`
    );
    return data.data ?? null;
  },

  updateShiftStatus: async (request: UpdateShiftStatusRequest) => {
    const { data } = await apiClient.put<{ success: boolean; data: CourierShiftResponse }>(
      '/v1/dispatch/couriers/shifts/status',
      request
    );
    return data.data;
  }
};
