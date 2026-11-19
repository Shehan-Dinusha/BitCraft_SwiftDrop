import { Field, SelectDropdown } from '@/design-system';
import type { SriLankanDistrict } from '@/services/api/orderApi';
import {
  DISTRICTS,
  provinceLabelOf,
  provinceOf,
  type BookingField,
  type BookingForm
} from './bookingForm';
import type { BookingErrors } from './validation';

/** Props every step receives from the wizard. */
export interface BookingStepProps {
  form: BookingForm;
  errors: BookingErrors;
  onChange: <K extends BookingField>(field: K, value: BookingForm[K]) => void;
  onBlur: (field: BookingField) => void;
}

/** Every district, in the order of the list. */
const districtOptions = DISTRICTS.map(({ value, label }) => ({ value, label }));

/**
 * House and street, area and landmark, then district, province, city and postal code, for the
 * pickup or the drop-off side. The customer picks the district and the province fills in from it
 * (read-only); the order stores the district. House and street become the order's first address
 * line, area and landmark the second.
 */
export function AddressFields({
  side,
  form,
  errors,
  onChange,
  onBlur
}: BookingStepProps & { side: 'pickup' | 'delivery' }) {
  const house = `${side}House` as const;
  const street = `${side}Street` as const;
  const area = `${side}Area` as const;
  const landmark = `${side}Landmark` as const;
  const province = `${side}Province` as const;
  const district = `${side}District` as const;
  const city = `${side}City` as const;
  const postal = `${side}PostalCode` as const;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2">
        <Field
          label="House or building no."
          required
          autoComplete={`section-${side} address-line1`}
          maxLength={40}
          value={form[house]}
          error={errors[house]}
          hint="For example No. 45 or 12/A"
          onChange={(event) => onChange(house, event.target.value)}
          onBlur={() => onBlur(house)}
        />
        <Field
          label="Street or road"
          required
          autoComplete={`section-${side} address-line2`}
          maxLength={150}
          value={form[street]}
          error={errors[street]}
          onChange={(event) => onChange(street, event.target.value)}
          onBlur={() => onBlur(street)}
        />
        <Field
          label="Area or locality"
          maxLength={98}
          value={form[area]}
          error={errors[area]}
          hint="Optional"
          onChange={(event) => onChange(area, event.target.value)}
          onBlur={() => onBlur(area)}
        />
        <Field
          label="Landmark"
          maxLength={100}
          value={form[landmark]}
          error={errors[landmark]}
          hint="Optional, for example opposite the post office"
          onChange={(event) => onChange(landmark, event.target.value)}
          onBlur={() => onBlur(landmark)}
        />
      </div>

      <div className="grid grid-cols-1 items-start gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <SelectDropdown
          label="District"
          required
          searchable
          placeholder="Choose a district"
          options={districtOptions}
          value={form[district] || undefined}
          error={errors[district]}
          onChange={(value) => {
            const next = value as SriLankanDistrict;
            onChange(district, next);
            // The province always follows the district, so the two can never disagree.
            onChange(province, provinceOf(next));
          }}
        />
        <Field
          label="Province"
          readOnly
          state="readOnly"
          tabIndex={-1}
          placeholder="Fills in from the district"
          value={provinceLabelOf(form[district])}
          onChange={() => undefined}
        />
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
        <Field
          label="Postal code"
          required
          inputMode="numeric"
          autoComplete={`section-${side} postal-code`}
          maxLength={5}
          value={form[postal]}
          error={errors[postal]}
          hint="5 digits"
          onChange={(event) => onChange(postal, event.target.value.replace(/\D/g, '').slice(0, 5))}
          onBlur={() => onBlur(postal)}
        />
      </div>
    </div>
  );
}
