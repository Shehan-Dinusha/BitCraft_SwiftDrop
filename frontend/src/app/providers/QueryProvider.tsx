import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactNode, useState } from 'react';
export function QueryProvider({ children }: { children: ReactNode }) {
  const [c] = useState(() => new QueryClient());
  return <QueryClientProvider client={c}>{children}</QueryClientProvider>;
}
