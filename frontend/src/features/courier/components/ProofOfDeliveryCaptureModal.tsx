import { Modal } from '@/design-system/primitives/Modal';
export function ProofOfDeliveryCaptureModal({
  open,
  onClose
}: {
  open: boolean;
  onClose: () => void;
}) {
  return (
    <Modal open={open} onClose={onClose} title="Proof of delivery">
      <div className="grid min-h-40 place-items-center bg-canvas">Camera capture placeholder</div>
    </Modal>
  );
}
