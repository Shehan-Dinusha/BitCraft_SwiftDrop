import { useState } from 'react';
import { Modal } from '@/design-system/primitives/Modal';
import { Button } from '@/design-system/primitives/Button';
import { Input } from '@/design-system/primitives/Input';
import { Field } from '@/design-system/primitives/Field';
import { StatusBanner } from '@/design-system/components/StatusBanner/StatusBanner';
import { deliveryApi } from '@/services/api/deliveryApi';
import { KeyRound, Camera, PenTool, CheckCircle2 } from 'lucide-react';

export interface ProofOfDeliveryCaptureModalProps {
  open: boolean;
  onClose: () => void;
  deliveryId: string;
  onVerified: () => void;
}

export function ProofOfDeliveryCaptureModal({
  open,
  onClose,
  deliveryId,
  onVerified
}: ProofOfDeliveryCaptureModalProps) {
  const [activeTab, setActiveTab] = useState<'otp' | 'photo_sig'>('otp');
  const [otp, setOtp] = useState('');
  const [recipientSignature, setRecipientSignature] = useState('');
  const [photoUrl, setPhotoUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!otp.trim()) {
      setErrorMsg('Please enter the 6-digit recipient OTP.');
      return;
    }
    try {
      setLoading(true);
      setErrorMsg(null);
      await deliveryApi.verifyProofOfDelivery(deliveryId, otp.trim());
      setSuccessMsg('Recipient OTP successfully verified! You may now complete delivery.');
      onVerified();
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'OTP verification failed';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleUploadProof = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!recipientSignature.trim() && !photoUrl.trim()) {
      setErrorMsg('Please provide either a photo URL or recipient signature name.');
      return;
    }
    try {
      setLoading(true);
      setErrorMsg(null);
      await deliveryApi.uploadProofOfDelivery(deliveryId, {
        photoUrl: photoUrl.trim() || 'https://swiftdrop.io/pod-evidence.jpg',
        recipientSignature: recipientSignature.trim() || 'Signed by recipient',
        confirmationOtp: otp.trim() || '000000'
      });
      setSuccessMsg('Proof of delivery recorded! You may now complete delivery.');
      onVerified();
      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to upload proof of delivery';
      setErrorMsg(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleSimulatePhoto = () => {
    setPhotoUrl('https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&auto=format&fit=crop');
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Capture Proof of Delivery (PoD)"
      description="Proof of delivery is required by security policy before transitioning to DELIVERED."
      size="md"
    >
      <div className="space-y-4 pt-2" data-density="touch">
        {errorMsg && (
          <StatusBanner tone="danger" title="Verification Error" onDismiss={() => setErrorMsg(null)}>
            {errorMsg}
          </StatusBanner>
        )}
        {successMsg && (
          <StatusBanner tone="success" title="Verified" onDismiss={() => setSuccessMsg(null)}>
            <div className="flex items-center gap-2">
              <CheckCircle2 className="h-5 w-5 text-emerald-500" />
              <span>{successMsg}</span>
            </div>
          </StatusBanner>
        )}

        {/* Tab selection for touch ergonomics */}
        <div className="grid grid-cols-2 gap-2 rounded-xl bg-slate-100 p-1 dark:bg-slate-800">
          <button
            type="button"
            className={`flex min-h-[44px] items-center justify-center gap-2 rounded-lg text-sm font-semibold transition-colors ${
              activeTab === 'otp'
                ? 'bg-white text-indigo-600 shadow-sm dark:bg-slate-700 dark:text-indigo-400'
                : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
            }`}
            onClick={() => {
              setActiveTab('otp');
              setErrorMsg(null);
            }}
          >
            <KeyRound className="h-4 w-4" />
            OTP Verification
          </button>
          <button
            type="button"
            className={`flex min-h-[44px] items-center justify-center gap-2 rounded-lg text-sm font-semibold transition-colors ${
              activeTab === 'photo_sig'
                ? 'bg-white text-indigo-600 shadow-sm dark:bg-slate-700 dark:text-indigo-400'
                : 'text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white'
            }`}
            onClick={() => {
              setActiveTab('photo_sig');
              setErrorMsg(null);
            }}
          >
            <Camera className="h-4 w-4" />
            Photo & Signature
          </button>
        </div>

        {activeTab === 'otp' ? (
          <form onSubmit={handleVerifyOtp} className="space-y-4 py-2">
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Ask the recipient for the 6-digit confirmation PIN sent to their SMS or notification channel.
            </p>
            <Field label="Recipient 6-digit OTP" required>
              <Input
                placeholder="e.g. 584920"
                value={otp}
                onChange={(e) => setOtp(e.target.value)}
                className="text-center font-mono text-xl tracking-widest min-h-[48px]"
                autoFocus
              />
            </Field>

            <Button
              type="submit"
              variant="primary"
              className="w-full min-h-[48px]"
              loading={loading}
              disabled={loading || !otp}
            >
              Verify OTP Code
            </Button>
          </form>
        ) : (
          <form onSubmit={handleUploadProof} className="space-y-3 py-2">
            <Field label="Recipient Signature or Name" required>
              <div className="relative">
                <Input
                  placeholder="Recipient sign-off name (e.g. P. Silva)"
                  value={recipientSignature}
                  onChange={(e) => setRecipientSignature(e.target.value)}
                  className="min-h-[44px] pl-9"
                />
                <PenTool className="absolute left-3 top-3.5 h-4 w-4 text-slate-400" />
              </div>
            </Field>

            <Field label="Parcel Dropoff Photo URL">
              <div className="space-y-2">
                <Input
                  placeholder="https://... or click camera simulation"
                  value={photoUrl}
                  onChange={(e) => setPhotoUrl(e.target.value)}
                  className="min-h-[44px]"
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={handleSimulatePhoto}
                  className="w-full min-h-[40px] text-xs"
                >
                  <Camera className="mr-2 h-4 w-4 text-indigo-500" />
                  Simulate Camera Snapshot
                </Button>
              </div>
            </Field>

            <Button
              type="submit"
              variant="primary"
              className="w-full min-h-[48px]"
              loading={loading}
              disabled={loading || (!recipientSignature && !photoUrl)}
            >
              Submit Proof of Delivery
            </Button>
          </form>
        )}
      </div>
    </Modal>
  );
}
