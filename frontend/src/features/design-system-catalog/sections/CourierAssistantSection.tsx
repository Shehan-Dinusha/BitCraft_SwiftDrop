import { useState } from 'react';
import {
  AssistantLauncher,
  AssistantPanel,
  FsmStatusBadge,
  SlideToConfirm,
  type AssistantMessage,
  type SlideToConfirmState
} from '@/design-system';
import { CatalogRow, DemoGrid, DemoRow } from '../components/catalog-kit';

const slideStates: { state: SlideToConfirmState; label: string }[] = [
  { state: 'idle', label: 'Idle' },
  { state: 'dragging', label: 'Dragging' },
  { state: 'holding', label: 'Holding' },
  { state: 'done', label: 'Done' },
  { state: 'locked', label: 'Locked' }
];

const openMessages: AssistantMessage[] = [
  { id: 1, role: 'user', text: 'Where is SD-7K4M-9QX2?' },
  {
    id: 2,
    role: 'assistant',
    text: 'It is In Transit from Gampaha to Colombo and should arrive tomorrow.',
    footer: <FsmStatusBadge state="IN_TRANSIT" size="sm" />
  },
  { id: 3, role: 'user', text: 'Can I change the delivery address?' },
  {
    id: 4,
    role: 'assistant',
    text: 'Only before a courier is assigned. This order already has a courier, so ask the dispatcher.'
  }
];

export function CourierAssistantSection() {
  const [messages, setMessages] = useState(openMessages);

  return (
    <>
      <CatalogRow label="Slide to confirm" hint="Forced states from Figma">
        <div className="max-w-[342px] space-y-3">
          {slideStates.map((item) => (
            <div key={item.state} className="space-y-1">
              <span className="text-[10px] uppercase tracking-wider text-muted">{item.label}</span>
              <SlideToConfirm
                label="Slide to confirm pickup"
                doneLabel="Picked up · cash collected"
                lockedLabel="Scan the QR to unlock"
                state={item.state}
                onConfirm={() => undefined}
              />
            </div>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow
        label="Live slide to confirm"
        hint="Drag to the end. The second one needs a 1.2 s hold"
      >
        <DemoGrid>
          <SlideToConfirm label="Slide to start trip" onConfirm={() => undefined} />
          <SlideToConfirm
            label="Slide to confirm pickup"
            holdMs={1200}
            doneLabel="Picked up · cash collected"
            onConfirm={() => undefined}
          />
        </DemoGrid>
      </CatalogRow>

      <CatalogRow label="Assistant" hint="Launcher, open panel and typing state">
        <DemoRow className="items-start gap-4">
          <AssistantLauncher onClick={() => undefined} />
          <AssistantPanel
            messages={messages}
            suggestions={['Fee estimate', 'Prohibited items', 'Talk to support']}
            onSend={(text) =>
              setMessages((current) => [...current, { id: current.length + 1, role: 'user', text }])
            }
            onClose={() => undefined}
          />
          <AssistantPanel messages={openMessages.slice(0, 1)} typing onSend={() => undefined} />
        </DemoRow>
      </CatalogRow>
    </>
  );
}
