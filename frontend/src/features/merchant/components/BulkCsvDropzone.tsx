import { useState, useRef } from 'react';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';
import { Button } from '@/design-system/primitives/Button';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { merchantApi, type BulkIngestBatchResponse } from '@/services/api/merchantApi';
import { UploadCloud, FileSpreadsheet, Download, CheckCircle2 } from 'lucide-react';

export interface BulkCsvDropzoneProps {
  merchantId: string;
  onBatchIngested?: (batch: BulkIngestBatchResponse) => void;
}

export function BulkCsvDropzone({ merchantId, onBatchIngested }: BulkCsvDropzoneProps) {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewRows, setPreviewRows] = useState<string[][]>([]);
  const [uploading, setUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleDownloadTemplate = () => {
    const csvContent =
      'recipient_name,recipient_phone,delivery_district,delivery_address,package_weight_kg,delivery_speed,external_ref,delivery_city,delivery_postal_code,pickup_city,pickup_postal_code,prohibited_items_declared\n' +
      'Test Recipient,+94771234567,COLOMBO,123 Galle Road,2.5,EXPRESS,SHOP-001,Colombo,00300,Gampaha,11000,true\n';

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'swiftdrop_orders_template.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (uploading) return;
    setSelectedFile(null);
    setPreviewRows([]);
    if (!file.name.toLowerCase().endsWith('.csv')) {
      setErrorMsg('Please select a valid .csv file');
      return;
    }

    setSelectedFile(file);
    setErrorMsg(null);
    setSuccessMsg(null);

    // Read first few lines for preview
    const reader = new FileReader();
    reader.onload = (event) => {
      const text = event.target?.result as string;
      if (text) {
        const lines = text.split('\n').filter((l) => l.trim().length > 0);
        const rows = lines.slice(0, 4).map((line) => {
          const cells: string[] = [];
          let cell = '';
          let quoted = false;
          for (let i = 0; i < line.length; i++) {
            if (line[i] === '"') {
              if (quoted && line[i + 1] === '"') {
                cell += '"';
                i++;
              } else quoted = !quoted;
            } else if (line[i] === ',' && !quoted) {
              cells.push(cell);
              cell = '';
            } else cell += line[i];
          }
          cells.push(cell);
          return cells;
        });
        setPreviewRows(rows);
      }
    };
    reader.readAsText(file);
  };

  const handleUpload = async () => {
    if (!selectedFile || uploading) return;

    try {
      setUploading(true);
      setErrorMsg(null);
      const res = await merchantApi.uploadCsvOrders(merchantId, selectedFile);
      setSuccessMsg(
        `Batch ${res.fileName} processed. Total: ${res.totalRecords}, Accepted: ${res.successCount}, Failed: ${res.failedCount}. ${res.errorSummary || ''}`
      );
      setSelectedFile(null);
      setPreviewRows([]);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      if (onBatchIngested) {
        onBatchIngested(res);
      }
    } catch (err: unknown) {
      const msg = apiErrorMessage(err, 'Failed to upload CSV orders');
      setErrorMsg(msg);
    } finally {
      setUploading(false);
    }
  };

  return (
    <SurfaceCard className="space-y-4 p-4" data-density="compact">
      <div className="flex items-center justify-between border-b border-border-subtle pb-3">
        <div className="flex items-center gap-2">
          <FileSpreadsheet className="h-4 w-4 text-emerald-500" />
          <h3 className="text-sm font-bold text-slate-900">Bulk CSV Order Ingestion</h3>
        </div>
        <Button variant="outline" className="h-8 px-2 text-xs" onClick={handleDownloadTemplate}>
          <Download className="mr-1 h-3.5 w-3.5 text-slate-500" />
          CSV Template
        </Button>
      </div>

      {errorMsg && (
        <StatusBanner tone="danger" title="Ingestion Error" onDismiss={() => setErrorMsg(null)}>
          {errorMsg}
        </StatusBanner>
      )}
      {successMsg && (
        <StatusBanner tone="success" title="Success" onDismiss={() => setSuccessMsg(null)}>
          {successMsg}
        </StatusBanner>
      )}

      {/* Drag and Drop Zone */}
      <label className="relative grid min-h-32 cursor-pointer place-items-center rounded-xl border-2 border-dashed border-border-subtle bg-slate-50/50 p-4 transition-colors hover:border-indigo-400 hover:bg-indigo-50/20">
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv"
          className="sr-only"
          disabled={uploading}
          aria-label="Select order CSV file"
          onChange={handleFileChange}
        />
        <div className="text-center">
          <UploadCloud className="mx-auto h-8 w-8 text-indigo-500" />
          <p className="mt-2 text-xs font-semibold text-slate-700">
            {selectedFile ? selectedFile.name : 'Select a CSV batch file'}
          </p>
          <p className="mt-0.5 text-[11px] text-slate-400">
            Supports standardized UTF-8 CSV with recipient address and Sri Lankan district
          </p>
        </div>
      </label>

      {/* Selected File & Preview */}
      {selectedFile && previewRows.length > 0 && (
        <div className="space-y-3 rounded-lg border border-border-subtle bg-card p-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-slate-700">
              Preview ({previewRows.length - 1} sample rows shown)
            </span>
            <span className="font-mono text-xs text-slate-500">
              {(selectedFile.size / 1024).toFixed(1)} KB
            </span>
          </div>

          <div className="max-h-32 overflow-x-auto text-[11px]">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-border-subtle font-semibold text-slate-500">
                  {previewRows[0]?.map((h, i) => (
                    <th key={i} className="pb-1 pr-2">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-border-subtle text-slate-700">
                {previewRows.slice(1).map((row, i) => (
                  <tr key={i}>
                    {row.map((cell, j) => (
                      <td key={j} className="py-1 pr-2 truncate max-w-[150px]">
                        {cell}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <Button
              variant="outline"
              className="h-8 text-xs"
              onClick={() => {
                setSelectedFile(null);
                setPreviewRows([]);
                if (fileInputRef.current) fileInputRef.current.value = '';
              }}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              className="h-8 text-xs"
              onClick={handleUpload}
              loading={uploading}
              disabled={uploading}
            >
              <CheckCircle2 className="mr-1.5 h-3.5 w-3.5" />
              Upload & Ingest Orders
            </Button>
          </div>
        </div>
      )}
    </SurfaceCard>
  );
}
