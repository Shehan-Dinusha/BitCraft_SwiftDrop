import { useEffect, useState, type FormEvent } from 'react';
import { Button, Field, Input } from '@/design-system';

export interface TrackingSearchFormProps {
  defaultValue?: string;
  loading?: boolean;
  buttonLabel?: string;
  onTrack: (trackingNumber: string) => void;
  onClear?: () => void;
}

export function TrackingSearchForm({
  defaultValue = '',
  loading = false,
  buttonLabel = 'Track parcel',
  onTrack,
  onClear
}: TrackingSearchFormProps) {
  const [value, setValue] = useState(defaultValue);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setValue(defaultValue);
    setError(null);
  }, [defaultValue]);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const next = value.trim();
    if (!next) {
      setError('Enter a tracking number to continue.');
      return;
    }
    setError(null);
    onTrack(next);
  };

  const handleClear = () => {
    setValue('');
    setError(null);
    onClear?.();
  };

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="flex flex-col gap-2 sm:flex-row sm:items-center gap-2"
    >
      <Field
        label="Tracking number"
        hint="Example: SD-2026-000123"
        error={error ?? undefined}
        className="min-w-0 flex-1"
      >
        <Input
          variant="search"
          value={value}
          onChange={(event) => {
            setValue(event.target.value);
            if (error) setError(null);
          }}
          onClear={handleClear}
          placeholder="Enter your tracking number"
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          inputClassName="font-mono tabular-nums"
        />
      </Field>
      <Button
        type="submit"
        loading={loading}
        loadingLabel="Tracking…"
        density="comfortable"
        className="w-full sm:w-auto shrink-0"
      >
        {buttonLabel}
      </Button>
    </form>
  );
}

export default TrackingSearchForm;
