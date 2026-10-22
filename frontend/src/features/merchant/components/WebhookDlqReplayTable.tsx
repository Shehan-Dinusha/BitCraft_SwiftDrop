import { HighDensityDataGrid } from '@/design-system/components/HighDensityDataGrid/HighDensityDataGrid';
export function WebhookDlqReplayTable() {
  return (
    <HighDensityDataGrid
      rows={[{ id: 'wh_001', status: 'RETRY_PENDING' }]}
      columns={[
        { key: 'id', label: 'ID' },
        { key: 'status', label: 'Status' }
      ]}
    />
  );
}
