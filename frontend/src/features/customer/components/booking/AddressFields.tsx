import { Field, SelectDropdown } from '@/design-system';
import type { SriLankanDistrict } from '@/services/api/orderApi';
import {
  districtsOf,
  provinceOf,
  provinceOptions,
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

/**
 * House and street, area and landmark, then province, district, city and postal code, for the
 * pickup or the drop-off side. The customer picks the province first and then a district from it;
 * the order stores the district. House and street become the order's first address line, area and
 * landmark the second.
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
  // A district set without a province (prefilled from the profile) still shows its province.
  const chosenProvince = form[province] || provinceOf(form[district]);

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
          placeholder="45 or 12/A"
          hint="For example No. 45 or 12/A"
          onChange={(event) => onChange(house, event.target.value)}
          onBlur={() => onBlur(house)}
        />
        <Field
          label="Street or road"
          placeholder="Galle Road"
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
          placeholder="Kollupitiya"
          maxLength={98}
          value={form[area]}
          error={errors[area]}
          hint="Optional"
          onChange={(event) => onChange(area, event.target.value)}
          onBlur={() => onBlur(area)}
        />
        <Field
          label="Landmark"
          placeholder="Opposite Liberty Plaza"
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
          value={chosenProvince || undefined}
          error={errors[province]}
          onChange={(value) => {
            const next = value as ProvinceId;
            onChange(province, next);
            // A district from another province no longer fits: clear it so it is chosen again.
            if (form[district] && provinceOf(form[district]) !== next) onChange(district, '');
          }}
        />
        <SelectDropdown
          label="District"
          required
          searchable
          disabled={!chosenProvince}
          placeholder={chosenProvince ? 'Choose a district' : 'Choose a province first'}
          options={districtsOf(chosenProvince)}
          value={form[district] || undefined}
          error={errors[district]}
          onChange={(value) => onChange(district, value as SriLankanDistrict)}
        />
        <Field
          label="City or town"
          placeholder="Colombo 03"
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
          placeholder="00300"
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
