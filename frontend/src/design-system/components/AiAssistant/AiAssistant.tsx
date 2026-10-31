import { Send, Sparkles, X } from 'lucide-react';
import { useId, useRef, useState, type ReactNode } from 'react';
import { cn } from '@/utils/cn';

export interface AssistantMessage {
  id: string | number;
  role: 'user' | 'assistant';
  text: string;
  /** Optional trailing element in an assistant bubble, e.g. a status badge. */
  footer?: ReactNode;
}

export interface AssistantLauncherProps {
  label?: string;
  onClick: () => void;
  expanded?: boolean;
  className?: string;
}

/** Figma AIAssistant / Launcher: pill button, "Ask SwiftDrop". */
export function AssistantLauncher({
  label = 'Ask SwiftDrop',
  onClick,
  expanded,
  className
}: AssistantLauncherProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-expanded={expanded}
      className={cn(
        'inline-flex items-center gap-2 rounded-full bg-brand-primary px-[18px] py-3.5 text-label text-brand-on shadow-ambient transition-colors hover:bg-brand-hover active:bg-brand-pressed',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
        className
      )}
    >
      <Sparkles aria-hidden="true" className="size-5" />
      {label}
    </button>
  );
}

export interface AssistantPanelProps {
  messages: AssistantMessage[];
  /** Quick prompts shown above the input. */
  suggestions?: string[];
  /** Shows the animated "typing" bubble (Figma: State = Typing). */
  typing?: boolean;
  onSend: (text: string) => void;
  onClose?: () => void;
  title?: string;
  subtitle?: string;
  placeholder?: string;
  className?: string;
}

/** Figma AIAssistantPanel: 380×520 chat card with header, messages, suggestion chips and an input. State = Open / Typing. */
export function AssistantPanel({
  messages,
  suggestions = [],
  typing = false,
  onSend,
  onClose,
  title = 'SwiftDrop assistant',
  subtitle = 'Answers from your orders and our FAQ',
  placeholder = 'Ask about an order or a fee…',
  className
}: AssistantPanelProps) {
  const [value, setValue] = useState('');
  const inputId = useId();
  const scroller = useRef<HTMLDivElement>(null);

  const send = (text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setValue('');
    window.setTimeout(() => scroller.current?.scrollTo({ top: scroller.current.scrollHeight }), 0);
  };

  return (
    <aside
      aria-label={title}
      className={cn(
        'flex h-[520px] w-[380px] max-w-full flex-col overflow-hidden rounded-2xl border border-border-subtle bg-card shadow-ambient',
        className
      )}
    >
      <header className="flex items-center gap-2.5 border-b border-border-subtle px-4 py-3.5">
        <span
          aria-hidden="true"
          className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-subtle text-brand-primary"
        >
          <Sparkles className="size-4" />
        </span>
        <div className="flex min-w-0 flex-1 flex-col">
          <p className="truncate text-body-strong text-primary">{title}</p>
          <p className="truncate text-caption text-secondary">{subtitle}</p>
        </div>
        {onClose ? (
          <button
            type="button"
            onClick={onClose}
            aria-label="Close assistant"
            className="inline-flex size-7 items-center justify-center rounded-md text-secondary hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
          >
            <X aria-hidden="true" className="size-[18px]" />
          </button>
        ) : null}
      </header>

      <div
        ref={scroller}
        role="log"
        aria-live="polite"
        className="flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto bg-canvas p-4"
      >
        {messages.map((message) => (
          <div
            key={message.id}
            className={cn('flex', message.role === 'user' ? 'justify-end' : 'justify-start')}
          >
            <div
              className={cn(
                'flex max-w-[260px] flex-col gap-1 rounded-xl px-3 py-2.5 text-body',
                message.role === 'user'
                  ? 'bg-brand-primary text-brand-on'
                  : 'border border-border-subtle bg-card text-primary'
              )}
            >
              <span>{message.text}</span>
              {message.footer ? <span className="self-start">{message.footer}</span> : null}
            </div>
          </div>
        ))}
        {typing ? (
          <div className="flex justify-start" role="status" aria-label="Assistant is typing">
            <div className="flex items-center gap-1 rounded-xl border border-border-subtle bg-card px-3 py-3.5">
              {[0, 1, 2].map((dot) => (
                <span
                  key={dot}
                  className="size-1.5 animate-pulse rounded-full bg-muted motion-reduce:animate-none"
                  style={{ animationDelay: `${dot * 150}ms` }}
                />
              ))}
            </div>
          </div>
        ) : null}
      </div>

      {suggestions.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 px-4 py-2">
          {suggestions.map((suggestion) => (
            <button
              key={suggestion}
              type="button"
              onClick={() => send(suggestion)}
              className="rounded-full border border-border-subtle px-2.5 py-[5px] text-caption text-primary transition-colors hover:bg-elevated focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus"
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}

      <form
        className="flex items-center gap-2 border-t border-border-subtle px-4 py-3"
        onSubmit={(event) => {
          event.preventDefault();
          send(value);
        }}
      >
        <label htmlFor={inputId} className="sr-only">
          Ask the assistant
        </label>
        <input
          id={inputId}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={placeholder}
          className="min-w-0 flex-1 rounded-[10px] border border-border-subtle bg-card px-3 py-2.5 text-body text-primary outline-none placeholder:text-muted focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-border-focus"
        />
        <button
          type="submit"
          aria-label="Send"
          disabled={!value.trim()}
          className="inline-flex size-10 shrink-0 items-center justify-center rounded-[10px] bg-brand-primary text-brand-on transition-colors hover:bg-brand-hover disabled:cursor-not-allowed disabled:bg-disabled-bg disabled:text-disabled-fg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
        >
          <Send aria-hidden="true" className="size-[18px]" />
        </button>
      </form>
    </aside>
  );
}

export interface AiAssistantProps {
  /** Resolve with the assistant's reply. Shows the typing bubble while pending. */
  onAsk: (question: string) => Promise<string>;
  suggestions?: string[];
  className?: string;
}

/** Launcher + panel wired together: the launcher opens a floating panel in the bottom-right corner. */
export function AiAssistant({ onAsk, suggestions, className }: AiAssistantProps) {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<AssistantMessage[]>([]);
  const [typing, setTyping] = useState(false);
  const nextId = useRef(1);

  const send = async (text: string) => {
    setMessages((current) => [...current, { id: nextId.current++, role: 'user', text }]);
    setTyping(true);
    try {
      const reply = await onAsk(text);
      setMessages((current) => [
        ...current,
        { id: nextId.current++, role: 'assistant', text: reply }
      ]);
    } finally {
      setTyping(false);
    }
  };

  return (
    <div className={cn('fixed bottom-4 right-4 z-40 flex flex-col items-end gap-3', className)}>
      {open ? (
        <AssistantPanel
          messages={messages}
          suggestions={messages.length === 0 ? suggestions : undefined}
          typing={typing}
          onSend={(text) => void send(text)}
          onClose={() => setOpen(false)}
        />
      ) : null}
      {open ? null : <AssistantLauncher onClick={() => setOpen(true)} expanded={open} />}
    </div>
  );
}

export default AiAssistant;
