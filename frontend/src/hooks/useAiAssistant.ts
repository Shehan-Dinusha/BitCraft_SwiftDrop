import { useState } from 'react';
export const useAiAssistant = () => {
  const [isOpen, set] = useState(false);
  return { isOpen, open: () => set(true), close: () => set(false) };
};
