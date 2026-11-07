import { Field } from '@/design-system';
import { normalisePhoneDigits } from './bookingForm';
import { AddressFields, type BookingStepProps } from './AddressFields';

export function PickupStep(props: BookingStepProps) {
  const { form, errors, onChange, onBlur } = props;

  return (
    <fieldset className="flex min-w-0 flex-col gap-4">
      <legend className="mb-1">
        <h2 className="text-h3 text-primary">Where should we pick it up?</h2>
      </legend>
      <p className="text-body text-secondary">The courier collects the parcel from this address.</p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <Field
          label="Contact name"
          required
          autoComplete="section-pickup name"
          maxLength={100}
          value={form.pickupContactName}
          error={errors.pickupContactName}
          hint="Who hands the parcel to the courier"
          onChange={(event) => onChange('pickupContactName', event.target.value)}
          onBlur={() => onBlur('pickupContactName')}
        />
        <Field
          label="Contact phone"
          required
          variant="phone"
          autoComplete="section-pickup tel-national"
          inputMode="numeric"
          value={form.pickupContactPhone}
          error={errors.pickupContactPhone}
          hint="The courier calls this number on arrival"
          onChange={(event) =>
            onChange('pickupContactPhone', normalisePhoneDigits(event.target.value))
          }
          onBlur={() => onBlur('pickupContactPhone')}
        />
        <Field
          label="Backup phone"
          variant="phone"
          autoComplete="section-pickup tel-national"
          inputMode="numeric"
          value={form.pickupContactAlternatePhone}
          error={errors.pickupContactAlternatePhone}
          hint="Optional. Called if the main number can't be reached"
          onChange={(event) =>
            onChange('pickupContactAlternatePhone', normalisePhoneDigits(event.target.value))
          }
          onBlur={() => onBlur('pickupContactAlternatePhone')}
        />
      </div>

      <AddressFields side="pickup" {...props} />
    </fieldset>
  );
}
