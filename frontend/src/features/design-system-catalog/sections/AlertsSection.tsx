import {
  Alert,
  Button,
  EmptyState,
  Toast,
  ToastProvider,
  useToast,
  type AlertTone,
  type EmptyStateType
} from '@/design-system';
import { CatalogRow, DemoGrid, DemoRow } from '../components/catalog-kit';

const tones: AlertTone[] = ['info', 'success', 'warning', 'danger'];
const emptyTypes: EmptyStateType[] = ['no-results', 'empty-queue', 'offline'];

const copy: Record<AlertTone, { title: string; message: string }> = {
  info: { title: 'Pickup time set', message: 'Courier Nimal will arrive between 2 pm and 4 pm.' },
  success: { title: 'Label ready', message: 'The A5 label PDF was generated for this parcel.' },
  warning: { title: 'Cash not yet handed over', message: 'Hand over today’s cash before 6 pm.' },
  danger: {
    title: 'Delivery attempt failed',
    message: 'Recipient unavailable. Next attempt tomorrow.'
  }
};

function ToastTrigger() {
  const { toast } = useToast();
  return (
    <DemoRow>
      {tones.map((tone) => (
        <Button
          key={tone}
          variant="secondary"
          onClick={() => toast({ tone, title: copy[tone].title, message: copy[tone].message })}
        >
          {`Show ${tone} toast`}
        </Button>
      ))}
    </DemoRow>
  );
}

export function AlertsSection() {
  return (
    <>
      <CatalogRow label="Alert" hint="Replaces StatusBanner for new work; same four tones">
        <div className="space-y-3">
          {tones.map((tone) => (
            <Alert
              key={tone}
              tone={tone}
              title={copy[tone].title}
              action="View details"
              onDismiss={() => undefined}
            >
              {copy[tone].message}
            </Alert>
          ))}
        </div>
      </CatalogRow>

      <CatalogRow label="Toast" hint="Closes after 5 s; at most 3 are shown at once">
        <div className="space-y-4">
          <DemoGrid>
            {tones.map((tone) => (
              <Toast key={tone} tone={tone} title={copy[tone].title} message={copy[tone].message} />
            ))}
          </DemoGrid>
          <ToastProvider>
            <ToastTrigger />
          </ToastProvider>
        </div>
      </CatalogRow>

      <CatalogRow label="Empty state" hint="No results, empty queue, offline">
        <DemoRow className="items-stretch">
          {emptyTypes.map((type) => (
            <EmptyState
              key={type}
              type={type}
              action={{
                label: type === 'offline' ? 'Retry' : 'Clear filters',
                onClick: () => undefined
              }}
            />
          ))}
        </DemoRow>
      </CatalogRow>
    </>
  );
}
