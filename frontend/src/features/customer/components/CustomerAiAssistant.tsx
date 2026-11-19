import { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { AssistantLauncher, AssistantPanel, Button, type AssistantMessage } from '@/design-system';
import { analyticsAiApi } from '@/services/api/analyticsAiApi';

export function CustomerAiAssistant() {
  const [open, setOpen] = useState(false);
  const [conversationId, setConversationId] = useState<string>();
  const [messages, setMessages] = useState<AssistantMessage[]>([]);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const sending = useRef(false);
  const failedQuestion = useRef('');
  const nextId = useRef(1);
  const wrapper = useRef<HTMLDivElement>(null);
  const activeRequest = useRef<AbortController>();
  const wasOpen = useRef(false);
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(Date.now());
  const retrySeconds = Math.max(0, Math.ceil((retryAt - now) / 1000));

  useEffect(() => () => activeRequest.current?.abort(), []);

  useEffect(() => {
    if (!retryAt) return;
    const timer = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= retryAt) setRetryAt(0);
    }, 1000);
    return () => window.clearInterval(timer);
  }, [retryAt]);

  useEffect(() => {
    if (!open) {
      if (wasOpen.current) wrapper.current?.querySelector('button')?.focus();
      wasOpen.current = false;
      return;
    }
    wasOpen.current = true;
    wrapper.current?.querySelector('input')?.focus();
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [open]);

  async function send(question: string, retry = false) {
    if (!question.trim() || sending.current || Date.now() < retryAt) return;
    sending.current = true;
    setPending(true);
    setError(undefined);
    if (!retry)
      setMessages((previous) => [
        ...previous,
        { id: nextId.current++, role: 'user', text: question }
      ]);
    const controller = new AbortController();
    activeRequest.current = controller;
    try {
      const reply = await analyticsAiApi.chat(
        { message: question, conversationId },
        controller.signal
      );
      setConversationId(reply.conversationId);
      setMessages((previous) => [
        ...previous,
        {
          id: nextId.current++,
          role: 'assistant',
          text: reply.reply,
          footer: reply.toolCalls.length ? (
            <span className="text-xs text-secondary">
              {reply.toolCalls
                .map(
                  (tool) =>
                    `${tool.name === 'track_parcel' ? 'Order tracking' : tool.name === 'calculate_estimate' ? 'Delivery estimate' : 'Request declined'}: ${tool.outcome}`
                )
                .join(' · ')}
            </span>
          ) : undefined
        }
      ]);
      failedQuestion.current = '';
    } catch (failure) {
      if (controller.signal.aborted) return;
      const status = axios.isAxiosError(failure) ? failure.response?.status : undefined;
      if (status === 429) {
        setRetryAt(Date.now() + 60_000);
        setNow(Date.now());
      }
      if (status === 404) setConversationId(undefined);
      failedQuestion.current = question;
      setError(
        status === 429
          ? 'Please wait 60 seconds before retrying.'
          : status === 404
            ? 'This conversation expired. Retry your complete question.'
            : status === 401 || status === 403
              ? 'Sign in with your customer account to use this assistant.'
              : 'The assistant is unavailable. Please try your message again.'
      );
    } finally {
      sending.current = false;
      setPending(false);
    }
  }

  return (
    <div
      ref={wrapper}
      className="relative bottom-20 flex max-w-[calc(100vw-3rem)] flex-col items-end gap-2 md:bottom-0"
    >
      {open ? (
        <>
          <AssistantPanel
            title="SwiftDrop order assistant"
            subtitle="Your orders and delivery estimates"
            placeholder="Ask about your order or a delivery fee..."
            messages={messages}
            typing={pending}
            disabled={retrySeconds > 0}
            className="max-h-[calc(100dvh-11rem)] md:max-h-[calc(100dvh-4rem)]"
            suggestions={
              messages.length
                ? undefined
                : ['Where is my parcel?', 'How much to send 2 kg from Colombo to Kandy?']
            }
            onSend={(question) => void send(question)}
            onClose={() => setOpen(false)}
            onReset={() => {
              if (sending.current) return;
              setConversationId(undefined);
              setMessages([]);
              setError(undefined);
              failedQuestion.current = '';
            }}
          />
          {error && (
            <div
              role="alert"
              className="w-full rounded-lg border border-border-subtle bg-card p-3 text-sm text-status-danger"
            >
              {error}{' '}
              <Button
                variant="ghost"
                density="comfortable"
                disabled={pending || retrySeconds > 0}
                onClick={() => void send(failedQuestion.current, true)}
              >
                {retrySeconds > 0 ? `Retry in ${retrySeconds}s` : 'Retry message'}
              </Button>
            </div>
          )}
        </>
      ) : (
        <AssistantLauncher label="Ask SwiftDrop" expanded={open} onClick={() => setOpen(true)} />
      )}
    </div>
  );
}
