import { useId } from 'react';
import { BookmarkPlus, MapPin } from 'lucide-react';
import { Checkbox, Field } from '@/design-system';
import type { SavedAddress } from '@/services/api/orderApi';
import { cn } from '@/utils/cn';
import { addressSummary } from './savedAddresses';

/** Whether this side of the booking is saved to the address book after it is placed, and under what name. */
export interface SaveChoice {
  save: boolean;
  label: string;
}

interface SavedAddressPickerProps {
  /** The customer's saved addresses; empty renders nothing. */
  addresses: SavedAddress[];
  /** The saved address this side was filled from, while it is unchanged. */
  selectedId: string | null;
  onSelect: (address: SavedAddress) => void;
}

/** Saved addresses at the top of the pickup or drop-off step: pick one to fill the form. */
export function SavedAddressPicker({ addresses, selectedId, onSelect }: SavedAddressPickerProps) {
  const listId = useId();
  if (addresses.length === 0) return null;

  return (
    <section aria-labelledby={listId} className="flex flex-col gap-2">
      <h3 id={listId} className="text-label font-semibold text-primary">
        Saved addresses
      </h3>
      <ul className="grid gap-2 sm:grid-cols-2">
        {addresses.map((address) => {
          const selected = address.addressId === selectedId;
          return (
            <li key={address.addressId}>
              <button
                type="button"
                aria-pressed={selected}
                onClick={() => onSelect(address)}
                className={cn(
                  'flex min-h-target w-full items-start gap-3 rounded-xl border p-3 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-border-focus',
                  selected
                    ? 'border-border-selected bg-brand-subtle'
                    : 'border-border-subtle bg-card hover:border-brand-primary'
                )}
              >
                <MapPin aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-brand-primary" />
                <span className="min-w-0">
                  <span className="block text-body-strong text-primary">{address.label}</span>
                  <span className="block truncate text-caption text-secondary">
                    {address.contactName} · {addressSummary(address)}
                  </span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

interface SaveAddressOptionProps {
  saveChoice: SaveChoice;
  onSaveChoice: (choice: SaveChoice) => void;
  labelError?: string;
  /** The address book is full, so a new address cannot be saved. */
  bookFull: boolean;
}

/** Under the address fields: tick to keep a newly typed address for next time. */
export function SaveAddressOption({
  saveChoice,
  onSaveChoice,
  labelError,
  bookFull
}: SaveAddressOptionProps) {
  return (
    <div className="flex flex-col gap-4">
      {bookFull ? (
        <p className="text-caption text-secondary">
          Your address book is full (20). Delete one in your profile to save this address.
        </p>
      ) : (
        <div className="flex flex-col gap-3 rounded-xl border border-border-subtle bg-canvas p-4">
          <Checkbox
            checked={saveChoice.save}
            onChange={(event) => onSaveChoice({ ...saveChoice, save: event.target.checked })}
            label={
              <span className="flex items-center gap-2 text-body text-primary">
                <BookmarkPlus aria-hidden="true" className="size-4 text-brand-primary" />
                Save this address for next time
              </span>
            }
          />
          {saveChoice.save ? (
            <Field
              label="Name for this address"
              required
              maxLength={40}
              hint="For example Home or Office"
              value={saveChoice.label}
              error={labelError}
              onChange={(event) => onSaveChoice({ ...saveChoice, label: event.target.value })}
              className="sm:max-w-xs"
            />
          ) : null}
        </div>
      )}
    </div>
  );
}
