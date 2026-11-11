import { useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { CustomerAiAssistant } from '@/features/customer/components/CustomerAiAssistant';

export function AiAssistantSlot() {
  const { pathname } = useLocation();
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);
  const isDevSession = useAuthStore((state) => state.isDevSession);
  const customerPage = pathname === '/customer' || pathname.startsWith('/customer/');
  if (!customerPage || !token || isDevSession || user?.role !== 'CUSTOMER') return null;
  return <CustomerAiAssistant key={user.id} />;
}

export default AiAssistantSlot;
