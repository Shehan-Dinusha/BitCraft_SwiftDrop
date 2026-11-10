import { Field, SelectDropdown } from '@/design-system';
import type { SriLankanDistrict } from '@/services/api/orderApi';
import {
  DISTRICTS,
  PROVINCES,
  provinceOf,
  type BookingField,
  type BookingForm,
  type ProvinceId
} from './bookingForm';
import type { BookingErrors } from './validation';

/** Props every step receives from the wizard. */
export interface BookingStepProps {
  form: BookingForm;
  errors: BookingErrors;
  onChange: <K extends BookingField>(field: K, value: BookingForm[K]) => void;
  onBlur: (field: BookingField) => void;
}

const provinceOptions = PROVINCES.map(({ id, label }) => ({ value: id, label }));
const districtLabelOf = new Map(DISTRICTS.map(({ value, label }) => [value, label]));

/** The districts of one province, or all of them while no province is chosen. */
function districtOptions(province: ProvinceId | '') {
  const districts = province
    ? (PROVINCES.find((item) => item.id === province)?.districts ?? [])
    : DISTRICTS.map(({ value }) => value);
  return districts.map((value) => ({ value, label: districtLabelOf.get(value) ?? value }));
}

/**
 * House and street, area and landmark, then province, district, city and postal code, for the
 * pickup or the drop-off side. The province only narrows the district list; the order stores the
 * district. House and street become the order's first address line, area and landmark the second.
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
          label="Province"
          required
          placeholder="Choose a province"
          options={provinceOptions}
          value={form[province] || undefined}
          error={errors[province]}
          onChange={(value) => {
            const next = value as ProvinceId;
            onChange(province, next);
            // A district from another province no longer fits.
            if (form[district] && provinceOf(form[district]) !== next) onChange(district, '');
          }}
        />
        <SelectDropdown
          label="District"
          required
          searchable
          placeholder="Choose a district"
          options={districtOptions(form[province])}
          value={form[district] || undefined}
          error={errors[district]}
          onChange={(value) => {
            const next = value as SriLankanDistrict;
            onChange(district, next);
            onChange(province, provinceOf(next));
          }}
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
          onChange={(event) => onChange(postal, event.target.value.replace(/D/g, '').slice(0, 5))}
          onBlur={() => onBlur(postal)}
        />
      </div>
    </div>
  );
}
