import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { UserCheck } from 'lucide-react';
import { Button, StatusBanner } from '@/design-system';
import { Modal } from '@/design-system/primitives/Modal';
import { apiErrorMessage } from '@/services/api/apiErrorMessage';
import { dispatchApi, type DispatchAssignmentResponse } from '@/services/api/dispatchApi';
import { vehicleLabel } from '@/utils/vehicles';
import { cn } from '@/utils/cn';
import { amount, onBreakUntil } from './courierFit';
import { Badge, CELL, DispatchTable, TableSkeleton } from './DispatchTable';
import { formatAge, type ShopGroup } from './dispatchQueues';

/** One row per shop: its parcels are collected together, once the shop says they are packed. */
export function ShopPickups({
  shops,
  now,
  onAssigned
}: {
  shops: ShopGroup[];
  now: number;
  onAssigned: (message: string) => void;
}) {
  const [assigning, setAssigning] = useState<ShopGroup | null>(null);
  if (shops.length === 0) return null;
  return (
    <section aria-label="Shop pickups" className="pb-4">
      <h2 className="px-3 pb-2 text-h3 text-primary">Shops</h2>
      <DispatchTable
        label="Shop pickups"
        headers={['Shop', 'Ready', 'Weight', 'Waiting', 'Action']}
      >
        {shops.map((shop) => (
          <tr key={shop.key} className="hover:bg-canvas">
            <td className={`${CELL} font-semibold text-primary`}>{shop.name}</td>
            <td className={CELL}>
              <span className="flex flex-wrap items-center gap-2">
                {shop.ready.length > 0 ? (
                  <Badge tone="success">{parcels(shop.ready.length)}</Badge>
                ) : null}
                {shop.notReady > 0 ? (
                  <Badge tone="neutral">{`${shop.notReady} not ready`}</Badge>
                ) : null}
              </span>
            </td>
            <td className={`${CELL} whitespace-nowrap tabular-nums text-secondary`}>
              {shop.ready.length > 0 ? `${amount(shop.weightKg)} kg` : '—'}
            </td>
            <td className={`${CELL} whitespace-nowrap text-secondary`}>
              {formatAge(shop.readySince, now) || '—'}
            </td>
            <td className={`${CELL} text-right`}>
              <Button
                density="compact"
                leftIcon={<UserCheck />}
                aria-label={`Assign ${shop.name}`}
                disabled={shop.ready.length === 0}
                onClick={() => setAssigning(shop)}
              >
                Assign
              </Button>
            </td>
          </tr>
        ))}
      </DispatchTable>
      {assigning ? (
        <AssignShop
          key={assigning.key}
          shop={assigning}
          onClose={() => setAssigning(null)}
          onAssigned={(message) => {
            setAssigning(null);
            onAssigned(message);
          }}
        />
      ) : null}
    </section>
  );
}

const parcels = (count: number) => `${count} ${count === 1 ? 'parcel' : 'parcels'}`;

function AssignShop({
  shop,
  onClose,
  onAssigned
}: {
  shop: ShopGroup;
  onClose: () => void;
  onAssigned: (message: string) => void;
}) {
  const [chosen, setChosen] = useState<string>();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const couriers = useQuery({
    queryKey: ['shop-couriers', shop.key],
    queryFn: () => dispatchApi.getShopCouriers(shop.merchantId),
    staleTime: 0,
    gcTime: 0,
    retry: false
  });
  const able = (couriers.data ?? []).filter((courier) => courier.fittingOrderIds.length > 0);
  // Most parcels first, so the best match is preselected until the dispatcher picks someone else.
  const selected = able.find((courier) => courier.courierId === chosen) ?? able[0];

  const assign = async () => {
    if (!selected || submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const assigned: DispatchAssignmentResponse[] = await dispatchApi.assignShop(
        shop.merchantId,
        selected.courierId,
        selected.fittingOrderIds
      );
      onAssigned(`${parcels(assigned.length)} of ${shop.name} assigned to ${selected.courierName}`);
    } catch (err) {
      setError(apiErrorMessage(err, 'Close this, refresh and try again.'));
      setSubmitting(false);
      void couriers.refetch();
    }
  };

  return (
    <Modal
      open
      onClose={() => {
        if (!submitting) onClose();
      }}
      title={`Assign ${shop.name}`}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <Button variant="outline" density="compact" disabled={submitting} onClick={onClose}>
            Cancel
          </Button>
          <Button
            density="compact"
            disabled={!selected}
            loading={submitting}
            onClick={() => void assign()}
          >
            {selected ? `Assign ${parcels(selected.fittingOrderIds.length)}` : 'Assign'}
          </Button>
        </div>
      }
    >
      <div className="space-y-4 text-caption">
        <p className="rounded-lg bg-canvas p-3 text-body text-primary">
          {parcels(shop.ready.length)} ready, {amount(shop.weightKg)} kg
        </p>
        {error ? (
          <StatusBanner tone="danger" title="Not assigned">
            {error}
          </StatusBanner>
        ) : null}
        {couriers.isError ? (
          <StatusBanner tone="danger" title="Couriers could not be loaded">
            Close this and try again.
          </StatusBanner>
        ) : couriers.data === undefined ? (
          <TableSkeleton />
        ) : able.length === 0 ? (
          <StatusBanner tone="warning" title="No courier can take these parcels now">
            They stay in the queue.
          </StatusBanner>
        ) : (
          <div role="radiogroup" aria-label="Courier" className="space-y-2">
            {able.map((courier, index) => {
              const left = shop.ready.length - courier.fittingOrderIds.length;
              const checked = courier.courierId === selected?.courierId;
              return (
                <label
                  key={courier.courierId}
                  className={cn(
                    'flex cursor-pointer flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border p-3',
                    'has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-border-focus',
                    checked
                      ? 'border-border-selected bg-brand-subtle'
                      : 'border-border-subtle bg-card hover:bg-canvas'
                  )}
                >
                  <input
                    type="radio"
                    name="shop-courier"
                    className="accent-brand-primary"
                    checked={checked}
                    disabled={submitting}
                    onChange={() => setChosen(courier.courierId)}
                  />
                  <span className="min-w-40 flex-1">
                    <span className="flex flex-wrap items-center gap-2 font-semibold text-primary">
                      {courier.courierName}
                      {index === 0 ? <Badge tone="success">Best match</Badge> : null}
                      {courier.breakEndsAt ? (
                        <Badge tone="info">{onBreakUntil(courier.breakEndsAt)}</Badge>
                      ) : null}
                    </span>
                    <span className="text-secondary">
                      {vehicleLabel(courier.vehicleType)} · {courier.activeParcels} of{' '}
                      {courier.maxCapacity} parcels
                    </span>
                  </span>
                  <span className="text-right text-secondary">
                    Takes {left === 0 ? 'all' : `${courier.fittingOrderIds.length}, leaves ${left}`}
                  </span>
                </label>
              );
            })}
          </div>
        )}
      </div>
    </Modal>
  );
}
