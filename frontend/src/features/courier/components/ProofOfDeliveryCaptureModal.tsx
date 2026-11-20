import { useEffect, useState, useRef } from 'react';
import { Modal } from '@/design-system/primitives/Modal';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { Field } from '@/design-system/primitives/Field';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { deliveryApi } from '@/services/api/deliveryApi';
import { Camera, PenTool, CheckCircle2, X } from 'lucide-react';

export interface ProofOfDeliveryCaptureModalProps {
  open: boolean;
  onClose: () => void;
  deliveryId: string;
  onVerified: () => void;
  onTransitionToDelivered?: () => Promise<void>;
}

export function ProofOfDeliveryCaptureModal({
  open,
  onClose,
  deliveryId,
  onVerified,
  onTransitionToDelivered
}: ProofOfDeliveryCaptureModalProps) {
  const [recipientName, setRecipientName] = useState('');
  const [recipientSignature, setRecipientSignature] = useState('');
  const [photoFile, setPhotoFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const previewCanvasRef = useRef<HTMLCanvasElement>(null);

  // Draw the preview onto a canvas from the decoded file instead of handing a URL derived
  // from user input to <img src>, so no DOM-sourced string ever reaches a URL/HTML sink.
  useEffect(() => {
    if (!photoFile) return;
    let cancelled = false;
    createImageBitmap(photoFile)
      .then((bitmap) => {
        const canvas = previewCanvasRef.current;
        if (cancelled || !canvas) {
          bitmap.close();
          return;
        }
        const scale = Math.min(1, 640 / bitmap.width);
        canvas.width = Math.round(bitmap.width * scale);
        canvas.height = Math.round(bitmap.height * scale);
        canvas.getContext('2d')?.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        bitmap.close();
      })
      .catch(() => {
        if (!cancelled) setErrorMsg('Could not read the selected photo. Please choose another image.');
      });
    return () => {
      cancelled = true;
    };
  }, [photoFile]);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setErrorMsg('Only JPEG and PNG image files are supported.');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setErrorMsg('Selected photo exceeds the 5 MB maximum size limit.');
      return;
    }

    setErrorMsg(null);
    setPhotoFile(file);
  };

  const handleClearPhoto = () => {
    setPhotoFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const handleSubmitPhotoAndComplete = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recipientName.trim()) {
      setErrorMsg('Recipient name is required.');
      return;
    }

    if (!photoFile) {
      setErrorMsg('Please capture or choose a photo of the delivered package.');
      return;
    }

    try {
      setLoading(true);
      setErrorMsg(null);

      // 1. Upload photo + recipient name; the backend marks the proof verified on success.
      await deliveryApi.uploadProofOfDeliveryMultipart(
        deliveryId,
        photoFile,
        recipientName.trim(),
        recipientSignature.trim() || undefined
      );

      setSuccessMsg('Photo uploaded and verified! Transitioning to DELIVERED...');
      onVerified();

      // 2. Transition to DELIVERED
      if (onTransitionToDelivered) {
        await onTransitionToDelivered();
      } else {
        await deliveryApi.transitionDelivery(deliveryId, {
          targetState: 'DELIVERED',
          actorRole: 'COURIER',
          notes: `Delivered to ${recipientName.trim()} with photo PoD`
        });
      }

      setTimeout(() => {
        onClose();
        handleClearPhoto();
        setRecipientName('');
        setRecipientSignature('');
      }, 1000);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to upload proof of delivery';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Capture Proof of Delivery (PoD)"
      description="Take a photo of the delivered package and record who received it before handover."
      size="md"
    >
      <div className="space-y-4 pt-2" data-density="touch">
        {errorMsg && (
          <StatusBanner tone="danger" title="PoD Error" onDismiss={() => setErrorMsg(null)}>
            {errorMsg}
          </StatusBanner>
        )}
        {successMsg && (
          <StatusBanner tone="success" title="Success" onDismiss={() => setSuccessMsg(null)}>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 text-emerald-500" />
              <span>{successMsg}</span>
            </div>
          </StatusBanner>
        )}

        <form onSubmit={handleSubmitPhotoAndComplete} className="space-y-4 py-1">
          <Field label="Recipient Name" required>
            <Input
              placeholder="Full name of recipient (e.g. Nimal Fernando)"
              value={recipientName}
              onChange={(e) => setRecipientName(e.target.value)}
              className="min-h-[48px]"
              required
              autoFocus
            />
          </Field>

          <Field label="Package Proof Photo" required>
            <div className="space-y-2">
              <input
                ref={fileInputRef}
                id="pod-photo-file-input"
                type="file"
                accept="image/*"
                capture="environment"
                className="hidden"
                onChange={handleFileChange}
              />

              {!photoFile ? (
                <label
                  htmlFor="pod-photo-file-input"
                  className="flex min-h-[96px] cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed border-indigo-300 bg-indigo-50/40 p-4 text-center transition-colors hover:bg-indigo-50 active:bg-indigo-100"
                >
                  <div className="grid h-10 w-10 place-items-center rounded-full bg-indigo-100 text-indigo-600">
                    <Camera className="h-5 w-5" />
                  </div>
                  <div>
                    <span className="text-sm font-bold text-indigo-900">Take Photo with Camera</span>
                    <p className="text-xs text-slate-500">or browse image from device (max 5 MB, JPEG/PNG)</p>
                  </div>
                </label>
              ) : (
                <div className="relative overflow-hidden rounded-xl border border-indigo-200 bg-slate-50 p-2">
                  <canvas
                    ref={previewCanvasRef}
                    role="img"
                    aria-label="Proof of Delivery preview"
                    className="max-h-48 w-full rounded-lg object-contain bg-black/5"
                  />
                  <div className="mt-2 flex items-center justify-between px-1">
                    <span className="truncate text-xs font-medium text-slate-600">
                      {photoFile?.name} ({(photoFile ? photoFile.size / 1024 : 0).toFixed(0)} KB)
                    </span>
                    <Button
                      type="button"
                      variant="destructive"
                      density="touch"
                      className="min-h-[36px] text-xs"
                      onClick={handleClearPhoto}
                    >
                      <X className="mr-1 h-3.5 w-3.5" />
                      Retake Photo
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </Field>

          <Field label="Recipient Signature or Note (Optional)">
            <div className="relative">
              <Input
                placeholder="Optional signature confirmation or notes"
                value={recipientSignature}
                onChange={(e) => setRecipientSignature(e.target.value)}
                className="min-h-[44px] pl-9"
              />
              <PenTool className="absolute left-3 top-3.5 h-4 w-4 text-slate-400" />
            </div>
          </Field>

          <Button
            type="submit"
            variant="primary"
            className="w-full min-h-[52px] text-base font-bold shadow-md"
            loading={loading}
            disabled={loading || !recipientName.trim() || !photoFile}
          >
            <CheckCircle2 className="mr-2 h-5 w-5" />
            Submit Proof & Complete Delivery
          </Button>
        </form>
      </div>
    </Modal>
  );
}
