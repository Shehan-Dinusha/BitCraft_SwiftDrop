import { Field, SelectDropdown, SpecialField } from '@/design-system';
import type { SriLankanDistrict } from '@/services/api/orderApi';
import { DISTRICTS, type BookingField, type BookingForm } from './bookingForm';
import type { BookingErrors } from './validation';

/** Props every step receives from the wizard. */
export interface BookingStepProps {
  form: BookingForm;
  errors: BookingErrors;
  onChange: <K extends BookingField>(field: K, value: BookingForm[K]) => void;
  onBlur: (field: BookingField) => void;
}

const districtOptions = DISTRICTS.map(({ value, label }) => ({ value, label }));

/** Address lines, city, district and postal code for the pickup or the drop-off side. */
export function AddressFields({
  side,
  form,
  errors,
  onChange,
  onBlur
}: BookingStepProps & { side: 'pickup' | 'delivery' }) {
  const line1 = `${side}AddressLine1` as const;
  const line2 = `${side}AddressLine2` as const;
  const city = `${side}City` as const;
  const district = `${side}District` as const;
  const postal = `${side}PostalCode` as const;

  return (
    <div className="flex flex-col gap-4">
      <Field
        label="Address"
        required
        autoComplete={`section-${side} address-line1`}
        maxLength={200}
        value={form[line1]}
        error={errors[line1]}
        hint="House number and street"
        onChange={(event) => onChange(line1, event.target.value)}
        onBlur={() => onBlur(line1)}
      />
      <Field
        label="Apartment, floor or landmark"
        autoComplete={`section-${side} address-line2`}
        maxLength={200}
        value={form[line2]}
        error={errors[line2]}
        hint="Optional"
        onChange={(event) => onChange(line2, event.target.value)}
        onBlur={() => onBlur(line2)}
      />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field
          label="City or town"
          required
          autoComplete={`section-${side} address-level2`}
          maxLength={100}
          value={form[city]}
          error={errors[city]}
          onChange={(event) => onChange(city, event.target.value)}
          onBlur={() => onBlur(city)}
        />
        <SelectDropdown
          label="District"
          required
          searchable
          placeholder="Choose a district"
          options={districtOptions}
          value={form[district] || undefined}
          error={errors[district]}
          onChange={(value) => onChange(district, value as SriLankanDistrict)}
        />
      </div>
      <SpecialField
        field="postal-code"
        label="Postal code"
        required
        className="sm:max-w-[220px]"
        value={form[postal]}
        error={errors[postal]}
        helper="5 digits"
        onChange={(value) => onChange(postal, value)}
      />
    </div>
  );
}
