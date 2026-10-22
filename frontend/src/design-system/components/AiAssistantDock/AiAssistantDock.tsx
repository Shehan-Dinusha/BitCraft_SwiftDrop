import { useRef, useState } from 'react';
import { Quote, Send, Sparkles } from 'lucide-react';
import { cn } from '@/utils/cn';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { Skeleton } from '@/design-system/primitives/Skeleton';
import { useDensityMode } from '@/design-system/providers/DensityProvider';
import { densityRecipes, type DensityMode } from '@/design-system/tokens/density';

export interface AiAssistantDockProps {
  title?: string;
  subtitle?: string;
  placeholder?: string;
  /** Starter prompts shown before the first question. */
  suggestions?: string[];
  /** Return a string to append an assistant reply; omit for a client-only dock. */
  onSubmit?: (question: string) => string | void | Promise<string | void>;
  density?: DensityMode;
  className?: string;
}

interface Entry {
  id: number;
  question: string;
  answer?: string;
}

const defaultSuggestions = [
  'Which zones are breaching SLA?',
  'How loaded is each courier?',
  "Summarise today's failed deliveries"
];

export function AiAssistantDock({
  title = 'Velocity AI Assistant',
  subtitle = 'Grounded in live operational data',
  placeholder = 'Ask about SLA, courier load, or zone demand',
  suggestions = defaultSuggestions,
  onSubmit,
  density,
  className
}: AiAssistantDockProps) {
  const contextDensity = useDensityMode();
  const resolvedDensity = density ?? contextDensity;
  const recipe = densityRecipes[resolvedDensity];
  const [entries, setEntries] = useState<Entry[]>([]);
  const [value, setValue] = useState('');
  const [pending, setPending] = useState(false);
  const nextId = useRef(1);

  const ask = async (question: string) => {
    const trimmed = question.trim();
    if (!trimmed || pending) return;

    const entry: Entry = { id: nextId.current++, question: trimmed };
    setEntries((current) => [...current, entry]);
    setValue('');

    if (!onSubmit) return;

    setPending(true);
    try {
      const answer = await onSubmit(trimmed);
      if (typeof answer === 'string' && answer.length > 0) {
        setEntries((current) =>
          current.map((item) => (item.id === entry.id ? { ...item, answer } : item))
        );
      }
    } finally {
      setPending(false);
    }
  };

  return (
    <aside
      aria-label={title}
      className={cn(
        'flex h-full flex-col rounded-xl border border-border-subtle bg-card text-primary',
        recipe.surfacePadding,
        className
      )}
    >
      <header className={cn('flex items-center', recipe.gap)}>
        <span
          aria-hidden="true"
          className={cn(
            'inline-flex shrink-0 items-center justify-center rounded-lg bg-brand-subtle text-brand-primary',
            recipe.target
          )}
        >
          <Sparkles className={recipe.icon} />
        </span>
        <div className="min-w-0">
          <p className={cn('truncate font-semibold', recipe.label)}>{title}</p>
          <p className={cn('truncate text-muted', recipe.caption)}>{subtitle}</p>
        </div>
      </header>

      <div
        className={cn('min-h-0 flex-1 overflow-y-auto', recipe.stackGap, 'mt-4 mb-4 flex flex-col')}
      >
        {entries.length === 0 ? (
          <div className={cn('flex flex-col', recipe.stackGap)}>
            <p className={cn('text-muted', recipe.caption)}>
              Ask a question about today&apos;s operation. Answers cite the operational data they
              use.
            </p>
            <div className="flex flex-wrap gap-2">
              {suggestions.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => ask(suggestion)}
                  className={cn(
                    'rounded-full border border-border-subtle bg-elevated px-3 py-1 text-left text-secondary transition-colors hover:border-border-focus hover:text-primary',
                    'focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
                    recipe.caption
                  )}
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : null}

        {entries.map((entry) => (
          <div key={entry.id} className="flex flex-col gap-2">
            <div
              className={cn(
                'self-end rounded-lg bg-brand-primary px-3 py-2 text-brand-on',
                recipe.caption
              )}
            >
              {entry.question}
            </div>
            {entry.answer ? (
              <div
                className={cn(
                  'rounded-lg border border-border-subtle bg-elevated',
                  recipe.menuItem
                )}
              >
                <p className={recipe.caption}>{entry.answer}</p>
                <p className={cn('mt-2 flex items-center gap-1 text-muted', recipe.caption)}>
                  <Quote aria-hidden="true" className="size-3" />
                  cited operational data
                </p>
              </div>
            ) : onSubmit ? (
              <Skeleton lines={2} className="w-2/3" />
            ) : null}
          </div>
        ))}
      </div>

      <form
        className="flex items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          void ask(value);
        }}
      >
        <Input
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          aria-label="Ask the assistant"
          density={resolvedDensity}
          className="flex-1"
        />
        <Button
          type="submit"
          density={resolvedDensity}
          rightIcon={<Send />}
          loading={pending}
          disabled={pending || !value.trim()}
        >
          Send
        </Button>
      </form>
    </aside>
  );
}

export default AiAssistantDock;
