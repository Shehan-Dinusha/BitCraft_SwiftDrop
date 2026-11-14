import { Check, X } from 'lucide-react';
import { cn } from '@/utils/cn';
import { passwordStrength, type PasswordContext } from '@/features/auth/passwordPolicy';

/**
 * Four-segment bars under new-password fields. Colours reuse the FSM tone triads that the design-system
 * Alert uses (failed = danger, in-transit = warning, picked-up = info, delivered = success).
 */
const TONES = {
  failed: { bar: 'bg-fsm-failed-fg', text: 'text-fsm-failed-fg' },
  warning: { bar: 'bg-fsm-in-transit-fg', text: 'text-fsm-in-transit-fg' },
  info: { bar: 'bg-fsm-picked-up-fg', text: 'text-fsm-picked-up-fg' },
  success: { bar: 'bg-fsm-delivered-fg', text: 'text-fsm-delivered-fg' }
} as const;
type Tone = keyof typeof TONES;

const STRENGTH_TONE: Record<1 | 2 | 3 | 4, Tone> = {
  1: 'failed',
  2: 'warning',
  3: 'info',
  4: 'success'
};

function SegmentBar({ filled, tone }: { filled: number; tone: Tone }) {
  return (
    <div className="grid grid-cols-4 gap-1.5" aria-hidden="true">
      {[1, 2, 3, 4].map((segment) => (
        <span
          key={segment}
          className={cn(
            'h-1.5 rounded-full transition-colors duration-200',
            segment <= filled ? TONES[tone].bar : 'bg-border-subtle'
          )}
        />
      ))}
    </div>
  );
}

export function PasswordStrengthMeter({
  password,
  context
}: {
  password: string;
  context?: PasswordContext;
}) {
  const { score, label, hints } = passwordStrength(password, context);
  if (score === 0) return null;
  const tone = STRENGTH_TONE[score as 1 | 2 | 3 | 4];

  return (
    <div className="space-y-1.5">
      <SegmentBar filled={score} tone={tone} />
      <p className="text-xs text-secondary" aria-live="polite">
        Password strength: <span className={cn('font-semibold', TONES[tone].text)}>{label}</span>
        {hints.length > 0 ? <span className="block text-muted">{hints.join(' ')}</span> : null}
      </p>
    </div>
  );
}

/**
 * The confirm field's counterpart: fills as the repeat matches what was typed so far, turns red as soon
 * as it diverges, and green when both are identical.
 */
export function PasswordMatchMeter({ password, confirm }: { password: string; confirm: string }) {
  if (!confirm) return null;

  const matches = confirm === password;
  const onTrack = !matches && password.startsWith(confirm);
  const filled = matches
    ? 4
    : onTrack
      ? Math.max(1, Math.min(3, Math.floor((confirm.length / password.length) * 4)))
      : 4;
  const tone: Tone = matches ? 'success' : onTrack ? 'info' : 'failed';
  const message = matches ? 'Passwords match' : onTrack ? 'Keep typing…' : "Passwords don't match";

  return (
    <div className="space-y-1.5">
      <SegmentBar filled={filled} tone={tone} />
      <p
        className={cn('flex items-center gap-1 text-xs font-semibold', TONES[tone].text)}
        aria-live="polite"
      >
        {matches ? <Check aria-hidden="true" className="size-3.5" /> : null}
        {!matches && !onTrack ? <X aria-hidden="true" className="size-3.5" /> : null}
        {message}
      </p>
    </div>
  );
}

export default PasswordStrengthMeter;
