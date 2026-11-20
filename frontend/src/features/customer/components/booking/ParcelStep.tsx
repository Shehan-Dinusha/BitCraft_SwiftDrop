import { Package, Timer, Zap } from 'lucide-react';
import { Field, RadioCard } from '@/design-system';
import type { OrderSpeed } from '@/services/api/orderApi';
import type { BookingStepProps } from './AddressFields';
import type { BookingField } from './bookingForm';
import { WEIGHT_MAX_KG } from './validation';

const SPEEDS: { value: OrderSpeed; title: string; caption: string; icon: JSX.Element }[] = [
  {
    value: 'STANDARD',
    title: 'Standard',
    caption: 'Our regular delivery service',
    icon: <Package />
  },
  {
    value: 'EXPRESS',
    title: 'Express',
    caption: 'Faster delivery for a higher fee',
    icon: <Zap />
  },
  {
    value: 'SAME_DAY',
    title: 'Same day',
    caption: 'Our fastest service, for a higher fee',
    icon: <Timer />
  }
];

const SIZE_EXAMPLE = { lengthCm: '30', widthCm: '20', heightCm: '10' } as const;

const decimal = (value: string) => value.replace(',', '.').replace(/[^\d.]/g, '');
const unit = (text: string) => <span className="shrink-0 text-body text-muted">{text}</span>;

export function ParcelStep({ form, errors, onChange, onBlur }: BookingStepProps) {
  const sizeField = (
    field: Extract<BookingField, 'lengthCm' | 'widthCm' | 'heightCm'>,
    label: string
  ) => (
    <Field
      label={label}
      required
      placeholder={SIZE_EXAMPLE[field]}
      inputMode="decimal"
      value={form[field]}
      error={errors[field]}
      trailing={unit('cm')}
      onChange={(event) => onChange(field, decimal(event.target.value))}
      onBlur={() => onBlur(field)}
    />
  );

  return (
    <fieldset className="flex min-w-0 flex-col gap-5">
      <legend className="mb-1">
        <h2 className="text-h3 text-primary">What are you sending?</h2>
      </legend>
      <p className="text-body text-secondary">
        Weight and size set the fee. The courier checks them at pickup.
      </p>

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[220px_minmax(0,1fr)]">
        <Field
          label="Weight"
          placeholder="2.5"
          required
          inputMode="decimal"
          className="sm:max-w-[220px]"
          value={form.actualWeightKg}
          error={errors.actualWeightKg}
          hint={`Up to ${WEIGHT_MAX_KG} kg`}
          trailing={unit('kg')}
          onChange={(event) => onChange('actualWeightKg', decimal(event.target.value))}
          onBlur={() => onBlur('actualWeightKg')}
        />

        {/* The caption sits under the fields, so the Weight and size labels share one row and the
            boxes line up. */}
        <div role="group" aria-labelledby="parcel-size-label" className="flex flex-col gap-2">
          <div className="grid grid-cols-3 items-start gap-3">
            {sizeField('lengthCm', 'Length')}
            {sizeField('widthCm', 'Width')}
            {sizeField('heightCm', 'Height')}
          </div>
          <span id="parcel-size-label" className="text-caption text-secondary">
            Size of the packed parcel, in centimetres
          </span>
        </div>
      </div>

      <div role="radiogroup" aria-labelledby="parcel-speed-label" className="flex flex-col gap-2">
        <span id="parcel-speed-label" className="text-label font-semibold text-primary">
          Delivery speed
        </span>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          {SPEEDS.map((speed) => (
            <RadioCard
              key={speed.value}
              name="booking-speed"
              value={speed.value}
              title={speed.title}
              caption={speed.caption}
              icon={speed.icon}
              checked={form.speed === speed.value}
              onChange={() => onChange('speed', speed.value)}
            />
          ))}
        </div>
      </div>


    </fieldset>
  );
}
