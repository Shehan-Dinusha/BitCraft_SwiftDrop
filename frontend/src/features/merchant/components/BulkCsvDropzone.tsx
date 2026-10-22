export function BulkCsvDropzone() {
  return (
    <label className="grid min-h-36 cursor-pointer place-items-center rounded-xl border-2 border-dashed text-sm">
      Drop CSV order batch
      <input type="file" accept=".csv" className="hidden" />
    </label>
  );
}
