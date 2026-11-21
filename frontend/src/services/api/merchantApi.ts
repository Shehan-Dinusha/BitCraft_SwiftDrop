import { apiClient } from './apiClient';
import type { components } from '@/types/generated/merchant-b2b-service';

export type MerchantAccountResponse = components['schemas']['MerchantAccountResponse'];
export type CreateMerchantRequest = components['schemas']['CreateMerchantRequest'];
export type ApiKeyResponse = components['schemas']['ApiKeyResponse'];
export type CreateApiKeyRequest = components['schemas']['CreateApiKeyRequest'];
export type BulkOrderIngestItem = components['schemas']['BulkOrderIngestItem'];
export type BulkIngestRequest = components['schemas']['BulkIngestRequest'];
export type BulkIngestBatchResponse = components['schemas']['BulkIngestBatchResponse'];
export type BulkBatchItemResponse = components['schemas']['BulkBatchItemResponse'];
export type SriLankanDistrict = components['schemas']['SriLankanDistrict'];
export type RegisterMerchantRequest = components['schemas']['RegisterMerchantRequest'];
export type MerchantApprovalResponse = components['schemas']['MerchantApprovalResponse'];
export type MerchantStatus = components['schemas']['MerchantStatus'];
export type MerchantOrderReceiptResponse = components['schemas']['MerchantOrderReceiptResponse'];

export const merchantApi = {
  register: async (request: RegisterMerchantRequest) => {
    const { data } = await apiClient.post<{ data: MerchantApprovalResponse }>(
      '/v1/merchants/register',
      request
    );
    return data.data;
  },
  /** The signed-in merchant's own application, or null before they have applied. */
  myApplication: async (): Promise<MerchantApprovalResponse | null> => {
    try {
      const { data } = await apiClient.get<{ data: MerchantApprovalResponse }>(
        '/v1/merchants/me/application'
      );
      return data.data;
    } catch (error) {
      if ((error as { response?: { status?: number } }).response?.status === 404) return null;
      throw error;
    }
  },
  /** A dispatcher always gets their own district; an admin every district or the one given. */
  listApplications: async (status?: MerchantStatus, district?: SriLankanDistrict) => {
    const { data } = await apiClient.get<{ data: MerchantApprovalResponse[] }>(
      '/v1/admin/merchants',
      { params: { status, district } }
    );
    return data.data;
  },
  /** Admin oversight: an approved merchant's API keys stop working until reactivated. */
  suspend: async (merchantId: string) => {
    const { data } = await apiClient.post<{ data: MerchantApprovalResponse }>(
      `/v1/admin/merchants/${merchantId}/suspend`
    );
    return data.data;
  },
  reactivate: async (merchantId: string) => {
    const { data } = await apiClient.post<{ data: MerchantApprovalResponse }>(
      `/v1/admin/merchants/${merchantId}/reactivate`
    );
    return data.data;
  },
  approve: async (merchantId: string) => {
    const { data } = await apiClient.post<{ data: MerchantApprovalResponse }>(
      `/v1/admin/merchants/${merchantId}/approve`
    );
    return data.data;
  },
  reject: async (merchantId: string, reason: string) => {
    const { data } = await apiClient.post<{ data: MerchantApprovalResponse }>(
      `/v1/admin/merchants/${merchantId}/reject`,
      { reason }
    );
    return data.data;
  },
  regenerateApiKey: async (merchantId: string, keyId: string) => {
    const { data } = await apiClient.post<{ data: ApiKeyResponse }>(
      `/v1/merchants/${merchantId}/api-keys/${keyId}/regenerate`
    );
    return data.data;
  },
  listOrders: async (merchantId: string, page = 0, size = 20) => {
    const { data } = await apiClient.get<{ data: MerchantOrderReceiptResponse[] }>(
      `/v1/merchants/${merchantId}/orders`,
      { params: { page, size } }
    );
    return data.data;
  },
  getMerchant: async (merchantId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: MerchantAccountResponse }>(
      `/v1/merchants/${merchantId}`
    );
    return data.data;
  },

  listMerchants: async () => {
    const { data } = await apiClient.get<{ success: boolean; data: MerchantAccountResponse[] }>(
      '/v1/merchants'
    );
    return data.data;
  },

  createMerchant: async (request: CreateMerchantRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: MerchantAccountResponse }>(
      '/v1/merchants',
      request
    );
    return data.data;
  },

  listApiKeys: async (merchantId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: ApiKeyResponse[] }>(
      `/v1/merchants/${merchantId}/api-keys`
    );
    return data.data;
  },

  generateApiKey: async (merchantId: string, request: CreateApiKeyRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: ApiKeyResponse }>(
      `/v1/merchants/${merchantId}/api-keys`,
      request
    );
    return data.data;
  },

  revokeApiKey: async (merchantId: string, keyId: string) => {
    const { data } = await apiClient.delete<{ success: boolean; data: ApiKeyResponse }>(
      `/v1/merchants/${merchantId}/api-keys/${keyId}`
    );
    return data.data;
  },

  ingestBulkOrders: async (merchantId: string, request: BulkIngestRequest) => {
    const { data } = await apiClient.post<{ success: boolean; data: BulkIngestBatchResponse }>(
      `/v1/merchants/${merchantId}/ingest/bulk`,
      request
    );
    return data.data;
  },

  uploadCsvOrders: async (merchantId: string, file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await apiClient.post<{ success: boolean; data: BulkIngestBatchResponse }>(
      `/v1/merchants/${merchantId}/ingest/csv`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      }
    );
    return data.data;
  },

  listBatches: async (merchantId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: BulkIngestBatchResponse[] }>(
      `/v1/merchants/${merchantId}/batches`
    );
    return data.data;
  },

  getBatch: async (batchId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: BulkIngestBatchResponse }>(
      `/v1/merchants/batches/${batchId}`
    );
    return data.data;
  },

  getBatchItems: async (batchId: string) => {
    const { data } = await apiClient.get<{ success: boolean; data: BulkBatchItemResponse[] }>(
      `/v1/merchants/batches/${batchId}/items`
    );
    return data.data;
  }
};
