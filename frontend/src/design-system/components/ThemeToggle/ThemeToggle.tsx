import { Moon, Sun } from 'lucide-react';
import { Button } from '@/design-system/primitives/Button';
import { useThemeStore } from '@/store/useThemeStore';

export interface ThemeToggleProps {
  /** `icon` for toolbars, `labelled` for settings surfaces. */
  appearance?: 'icon' | 'labelled';
}

export function ThemeToggle({ appearance = 'icon' }: ThemeToggleProps) {
  const theme = useThemeStore((state) => state.theme);
  const toggleTheme = useThemeStore((state) => state.toggleTheme);
  const isDark = theme === 'dark';

  return (
    <Button
      type="button"
      variant="ghost"
      className="text-secondary"
      aria-label={isDark ? 'Switch to light theme' : 'Switch to dark theme'}
      aria-pressed={isDark}
      onClick={toggleTheme}
      leftIcon={isDark ? <Sun /> : <Moon />}
    >
      {appearance === 'labelled' ? (isDark ? 'Light theme' : 'Dark theme') : null}
    </Button>
  );
}

export default ThemeToggle;
