import { apiClient } from './apiClient';
export const deliveryApi = {
  list: () => apiClient.get('/v1/deliveries'),
  get: (id: string) => apiClient.get(`/v1/deliveries/${id}`)
};
