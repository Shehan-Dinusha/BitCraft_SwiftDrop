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
export type WorkdayResponse = components['schemas']['WorkdayResponse'];
export type ClosedDayResponse = components['schemas']['ClosedDayResponse'];
export type ClosedDayRequest = components['schemas']['ClosedDayRequest'];

export interface PagedDispatchResult {
  content: DispatchAssignmentResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export type ShopCourierSuggestionResponse = components['schemas']['ShopCourierSuggestionResponse'];

export const dispatchApi = {
  /** A dispatcher always gets their own district; `district` only applies to a system admin. */
  getAssignments: async (params?: {
    status?: AssignmentStatus;
    page?: number;
    size?: number;
    district?: SriLankanDistrict;
  }) => {
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

  getUnassignedParcels: async (district?: SriLankanDistrict) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse[] }>(
      '/v1/dispatch/unassigned',
      { params: { district } }
    );
    return data.data;
  },

  /** Parcels handed in at the district's hub and waiting to be marked Outbound. */
  getReadyToSend: async (district?: SriLankanDistrict) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse[] }>(
      '/v1/dispatch/ready-to-send',
      { params: { district } }
    );
    return data.data;
  },

  /** Open orders delivered into the district from elsewhere; read-only for its dispatcher. */
  getIncoming: async (district?: SriLankanDistrict) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse[] }>(
      '/v1/dispatch/incoming',
      { params: { district } }
    );
    return data.data;
  },

  /** Failed deliveries the district deals with, and returns coming to its hub. */
  getReturns: async (district?: SriLankanDistrict) => {
    const { data } = await apiClient.get<{ success: boolean; data: DispatchAssignmentResponse[] }>(
      '/v1/dispatch/returns',
      { params: { district } }
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

  /** Couriers on shift and which of a shop's ready parcels each has room for. */
  getShopCouriers: async (merchantId: string, district?: SriLankanDistrict) => {
    const { data } = await apiClient.get<{ data: ShopCourierSuggestionResponse[] }>(
      `/v1/dispatch/shops/${merchantId}/couriers`,
      { params: { district } }
    );
    return data.data;
  },

  /** All of these parcels to one courier, or none. */
  assignShop: async (merchantId: string, courierId: string, orderIds: string[]) => {
    const { data } = await apiClient.post<{ data: DispatchAssignmentResponse[] }>(
      `/v1/dispatch/shops/${merchantId}/assign`,
      { courierId, orderIds }
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
  },

  /** The courier leaves the hub on their run; refused while hub parcels are still to load. */
  startRun: async (courierId: string) => {
    const { data } = await apiClient.post<{ success: boolean; data: CourierShiftResponse }>(
      `/v1/dispatch/couriers/shifts/${courierId}/run`
    );
    return data.data;
  },

  /** Today's working day, hours, lunch break and coming holidays (Sri Lanka time). */
  getWorkday: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: WorkdayResponse }>(
      '/v1/dispatch/calendar/today'
    );
    return data.data;
  },

  /** System admin: close a holiday. Returns the holidays from today on. */
  addClosedDay: async (request: ClosedDayRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: ClosedDayResponse[] }>(
      '/v1/dispatch/calendar/closed-days',
      request
    );
    return data.data;
  },

  /** System admin: reopen a holiday (`YYYY-MM-DD`). Returns the holidays from today on. */
  removeClosedDay: async (day: string) => {
    const { data } = await apiClient.delete<{ success: boolean; data: ClosedDayResponse[] }>(
      `/v1/dispatch/calendar/closed-days/${day}`
    );
    return data.data;
  }
};
