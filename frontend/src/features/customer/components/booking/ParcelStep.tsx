import { useEffect, useRef, useState } from 'react';
import { ChevronDown, Package, Timer, Zap } from 'lucide-react';
import { Button, Field, RadioCard, Toggle } from '@/design-system';
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

const decimal = (value: string) => value.replace(',', '.').replace(/[^\d.]/g, '');
const unit = (text: string) => <span className="shrink-0 text-body text-muted">{text}</span>;

export function ParcelStep({ form, errors, onChange, onBlur }: BookingStepProps) {
  const [showExtras, setShowExtras] = useState(
    Boolean(
      form.itemDescription || form.declaredValue || errors.itemDescription || errors.declaredValue
    )
  );
  const [focusExtras, setFocusExtras] = useState(false);
  const contentsRef = useRef<HTMLInputElement>(null);

  // Revealing the optional fields removes the button that had focus; hand focus to the new field.
  useEffect(() => {
    if (focusExtras && showExtras) {
      contentsRef.current?.focus();
      setFocusExtras(false);
    }
  }, [focusExtras, showExtras]);

  const sizeField = (
    field: Extract<BookingField, 'lengthCm' | 'widthCm' | 'heightCm'>,
    label: string
  ) => (
    <Field
      label={label}
      required
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

        <div role="group" aria-labelledby="parcel-size-label" className="flex flex-col gap-2">
          <span id="parcel-size-label" className="text-label font-semibold text-primary">
            Size of the packed parcel
          </span>
          <div className="grid grid-cols-3 gap-3">
            {sizeField('lengthCm', 'Length')}
            {sizeField('widthCm', 'Width')}
            {sizeField('heightCm', 'Height')}
          </div>
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

      <div className="flex flex-col gap-3 rounded-xl border border-border-subtle p-4">
        <Toggle
          label="Collect cash from the recipient"
          checked={form.collectCash}
          onChange={(event) => {
            onChange('collectCash', event.target.checked);
            if (!event.target.checked) onChange('codAmount', '');
          }}
        />
        {form.collectCash ? (
          <Field
            label="Amount to collect"
            required
            variant="currency"
            currency="LKR"
            inputMode="decimal"
            className="sm:max-w-[280px]"
            value={form.codAmount}
            error={errors.codAmount}
            hint="The courier collects this in cash on delivery"
            onChange={(event) => onChange('codAmount', decimal(event.target.value))}
            onBlur={() => onBlur('codAmount')}
          />
        ) : null}
      </div>

      {showExtras ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field
            ref={contentsRef}
            label="What's inside"
            maxLength={512}
            value={form.itemDescription}
            error={errors.itemDescription}
            hint="Optional, e.g. documents or clothing"
            onChange={(event) => onChange('itemDescription', event.target.value)}
            onBlur={() => onBlur('itemDescription')}
          />
          <Field
            label="Value of the contents"
            variant="currency"
            currency="LKR"
            inputMode="decimal"
            value={form.declaredValue}
            error={errors.declaredValue}
            hint="Optional"
            onChange={(event) => onChange('declaredValue', decimal(event.target.value))}
            onBlur={() => onBlur('declaredValue')}
          />
        </div>
      ) : (
        <Button
          type="button"
          variant="ghost"
          className="self-start"
          rightIcon={<ChevronDown />}
          onClick={() => {
            setShowExtras(true);
            setFocusExtras(true);
          }}
        >
          Add contents and value
        </Button>
      )}
    </fieldset>
  );
}
