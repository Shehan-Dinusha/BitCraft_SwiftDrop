import { PERSONA_ROLES, type PersonaRole } from '@/types/contracts';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';

export interface PersonaSwitcherDevBarProps {
  /** Called after the session switches, e.g. to navigate to the persona home. */
  onSwitch?: (role: PersonaRole) => void;
}

export function PersonaSwitcherDevBar({ onSwitch }: PersonaSwitcherDevBarProps) {
  const activeRole = useAuthStore((state) => state.role);
  const setSession = useAuthStore((state) => state.setSession);

  return (
    <div className="flex flex-wrap items-center gap-1 border-b border-border-subtle bg-elevated px-3 py-1.5">
      <span className="mr-1 text-[10px] font-semibold uppercase tracking-wider text-muted">
        Dev persona
      </span>
      {PERSONA_ROLES.map((role) => (
        <button
          key={role}
          type="button"
          aria-pressed={role === activeRole}
          onClick={() => {
            setSession('dev-token', role);
            onSwitch?.(role);
          }}
          className={cn(
            'rounded px-2 py-0.5 text-[11px] font-semibold transition-colors',
            role === activeRole
              ? 'bg-brand-primary text-brand-on'
              : 'text-secondary hover:bg-card hover:text-primary'
          )}
        >
          {role.replace(/_/g, ' ')}
        </button>
      ))}
    </div>
  );
}

export default PersonaSwitcherDevBar;
