import { Camera, CircleAlert, RefreshCw, Trash2 } from 'lucide-react';
import { useId, type ChangeEvent } from 'react';
import { cn } from '@/utils/cn';

export type PhotoUploadState = 'empty' | 'captured' | 'uploading' | 'error';

export interface PhotoUploadProps {
  state: PhotoUploadState;
  /** Called with the file the courier captured or picked. */
  onCapture?: (file: File) => void;
  onRetake?: (file: File) => void;
  onRemove?: () => void;
  /** Error state: tap to try again. */
  onRetry?: () => void;
  /** Object URL or data URL of the captured photo. */
  previewSrc?: string;
  /** 0–100 while uploading. */
  progress?: number;
  title?: string;
  hint?: string;
  className?: string;
}

const frame =
  'flex h-[170px] w-full max-w-60 flex-col items-center justify-center gap-2 rounded-xl border p-3 text-center';

/** Figma PhotoUpload: proof-of-delivery photo. State = Empty / Captured / Uploading / Error. */
export function PhotoUpload({
  state,
  onCapture,
  onRetake,
  onRemove,
  onRetry,
  previewSrc,
  progress = 0,
  title = 'Take delivery photo',
  hint = 'Required to mark Delivered',
  className
}: PhotoUploadProps) {
  const inputId = useId();
  const clamped = Math.min(100, Math.max(0, progress));

  const handle = (callback?: (file: File) => void) => (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) callback?.(file);
    event.target.value = '';
  };

  if (state === 'captured') {
    return (
      <div className={cn(frame, 'border-border-subtle bg-elevated', className)}>
        {previewSrc ? (
          <img
            src={previewSrc}
            alt="Delivery photo preview"
            className="h-[100px] w-full rounded-lg object-cover"
          />
        ) : (
          <div aria-hidden="true" className="h-[100px] w-full rounded-lg bg-border-subtle" />
        )}
        <div className="flex items-center gap-3 text-label">
          <label
            htmlFor={inputId}
            className="inline-flex cursor-pointer items-center gap-1 text-brand-primary has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-border-focus"
          >
            <input
              id={inputId}
              type="file"
              accept="image/*"
              capture="environment"
              className="sr-only"
              onChange={handle(onRetake)}
            />
            <Camera aria-hidden="true" className="size-4" />
            Retake
          </label>
          <button
            type="button"
            onClick={onRemove}
            className="inline-flex items-center gap-1 text-danger-hover focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus"
          >
            <Trash2 aria-hidden="true" className="size-4" />
            Remove
          </button>
        </div>
      </div>
    );
  }

  if (state === 'uploading') {
    return (
      <div className={cn(frame, 'border-border-subtle bg-canvas', className)} role="status">
        <Camera aria-hidden="true" className="size-7 text-muted" />
        <p className="text-body-strong text-primary">Uploading… {Math.round(clamped)}%</p>
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(clamped)}
          className="h-1.5 w-[180px] overflow-hidden rounded-full bg-border-subtle"
        >
          <div className="h-full rounded-full bg-brand-primary" style={{ width: `${clamped}%` }} />
        </div>
        <p className="text-caption text-secondary">Saved on phone if you go offline</p>
      </div>
    );
  }

  if (state === 'error') {
    return (
      <button
        type="button"
        onClick={onRetry}
        className={cn(
          frame,
          'border-status-danger bg-danger-subtle focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-border-focus',
          className
        )}
      >
        <CircleAlert aria-hidden="true" className="size-7 text-danger-hover" />
        <span className="text-body-strong text-danger-hover">Upload failed</span>
        <span className="inline-flex items-center gap-1 text-caption text-primary">
          <RefreshCw aria-hidden="true" className="size-3" />
          Tap to try again. Photo is kept on this phone.
        </span>
      </button>
    );
  }

  return (
    <label
      htmlFor={inputId}
      className={cn(
        frame,
        'cursor-pointer border-border-subtle bg-canvas transition-colors hover:bg-elevated has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-border-focus',
        className
      )}
    >
      <input
        id={inputId}
        type="file"
        accept="image/*"
        capture="environment"
        className="sr-only"
        onChange={handle(onCapture)}
      />
      <Camera aria-hidden="true" className="size-7 text-brand-primary" />
      <span className="text-body-strong text-primary">{title}</span>
      <span className="text-caption text-secondary">{hint}</span>
    </label>
  );
}

export default PhotoUpload;
