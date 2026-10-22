import { Card } from '@/design-system/primitives/Card';
import { BulkCsvDropzone } from '../components/BulkCsvDropzone';
import { ApiKeyManagerCard } from '../components/ApiKeyManagerCard';
export function MerchantPortalDashboard() {
  return (
    <main className="space-y-5 p-4">
      <h1 className="text-2xl font-bold">Merchant operations</h1>
      <div className="grid gap-4 md:grid-cols-2">
        <Card>
          <BulkCsvDropzone />
        </Card>
        <ApiKeyManagerCard />
      </div>
    </main>
  );
}
