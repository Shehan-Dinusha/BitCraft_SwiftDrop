import { Card } from '@/design-system/primitives/Card';
import { DeliveryProgressStepper } from '@/design-system/components/DeliveryProgressStepper/DeliveryProgressStepper';
import { LiveMapCanvas } from '../components/LiveMapCanvas';
export function LiveTrackingPage() {
  return (
    <main className="mx-auto max-w-5xl space-y-5 p-6">
      <h1 className="text-3xl font-bold">Live tracking</h1>
      <DeliveryProgressStepper current="IN_TRANSIT" />
      <Card>
        <LiveMapCanvas />
      </Card>
    </main>
  );
}
