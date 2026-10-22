import axios from 'axios';
import { useAuthStore } from '@/store/useAuthStore';
export const apiClient = axios.create({ baseURL: '/api' });
apiClient.interceptors.request.use((c) => {
  const t = useAuthStore.getState().token;
  if (t) c.headers.Authorization = `Bearer ${t}`;
  return c;
});
