import { apiClient } from './apiClient';
export const orderApi = {
  list: () => apiClient.get('/v1/orders'),
  get: (id: string) => apiClient.get(`/v1/orders/${id}`)
};
