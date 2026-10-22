import { Moon, Sun } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { useEffect, useState } from 'react';

export function ThemeToggle() {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains('dark'));

  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark);
  }, [dark]);

  return (
    <Button
      type="button"
      variant="ghost"
      aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      onClick={() => setDark(value => !value)}
    >
      {dark ? <Sun size={18} /> : <Moon size={18} />}
    </Button>
  );
}
