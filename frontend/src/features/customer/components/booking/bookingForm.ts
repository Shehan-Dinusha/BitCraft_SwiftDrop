import type {
  CreateOrderRequest,
  FeeEstimateRequest,
  OrderSpeed,
  SriLankanDistrict
} from '@/services/api/orderApi';

/**
 * Wizard state. Numbers are kept as the strings the user typed so a half-typed value such as `2.`
 * survives a re-render; they are converted once, in `toCreateOrderRequest` / `toFeeEstimateRequest`.
 * Phone numbers hold only the 9 digits after the `+94` prefix shown by the phone input.
 */
export interface BookingForm {
  pickupContactName: string;
  pickupContactPhone: string;
  pickupAddressLine1: string;
  pickupAddressLine2: string;
  pickupCity: string;
  pickupDistrict: SriLankanDistrict | '';
  pickupPostalCode: string;
  recipientName: string;
  recipientPhone: string;
  recipientEmail: string;
  deliveryAddressLine1: string;
  deliveryAddressLine2: string;
  deliveryCity: string;
  deliveryDistrict: SriLankanDistrict | '';
  deliveryPostalCode: string;
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  actualWeightKg: string;
  speed: OrderSpeed;
  itemDescription: string;
  declaredValue: string;
  /** UI only: the customer turned on "Collect cash on delivery", so an amount is required. */
  collectCash: boolean;
  codAmount: string;
  prohibitedItemsDeclared: boolean;
}

export type BookingField = keyof BookingForm;

export const initialBookingForm: BookingForm = {
  pickupContactName: '',
  pickupContactPhone: '',
  pickupAddressLine1: '',
  pickupAddressLine2: '',
  pickupCity: '',
  pickupDistrict: '',
  pickupPostalCode: '',
  recipientName: '',
  recipientPhone: '',
  recipientEmail: '',
  deliveryAddressLine1: '',
  deliveryAddressLine2: '',
  deliveryCity: '',
  deliveryDistrict: '',
  deliveryPostalCode: '',
  lengthCm: '',
  widthCm: '',
  heightCm: '',
  actualWeightKg: '',
  speed: 'STANDARD',
  itemDescription: '',
  declaredValue: '',
  collectCash: false,
  codAmount: '',
  prohibitedItemsDeclared: false
};

export const DISTRICTS: { value: SriLankanDistrict; label: string }[] = [
  { value: 'COLOMBO', label: 'Colombo' },
  { value: 'GAMPAHA', label: 'Gampaha' },
  { value: 'KALUTARA', label: 'Kalutara' },
  { value: 'KANDY', label: 'Kandy' },
  { value: 'MATALE', label: 'Matale' },
  { value: 'NUWARA_ELIYA', label: 'Nuwara Eliya' },
  { value: 'GALLE', label: 'Galle' },
  { value: 'MATARA', label: 'Matara' },
  { value: 'HAMBANTOTA', label: 'Hambantota' },
  { value: 'JAFFNA', label: 'Jaffna' },
  { value: 'KILINOCHCHI', label: 'Kilinochchi' },
  { value: 'MANNAR', label: 'Mannar' },
  { value: 'VAVUNIYA', label: 'Vavuniya' },
  { value: 'MULLAITIVU', label: 'Mullaitivu' },
  { value: 'BATTICALOA', label: 'Batticaloa' },
  { value: 'AMPARA', label: 'Ampara' },
  { value: 'TRINCOMALEE', label: 'Trincomalee' },
  { value: 'KURUNEGALA', label: 'Kurunegala' },
  { value: 'PUTTALAM', label: 'Puttalam' },
  { value: 'ANURADHAPURA', label: 'Anuradhapura' },
  { value: 'POLONNARUWA', label: 'Polonnaruwa' },
  { value: 'BADULLA', label: 'Badulla' },
  { value: 'MONARAGALA', label: 'Monaragala' },
  { value: 'RATNAPURA', label: 'Ratnapura' },
  { value: 'KEGALLE', label: 'Kegalle' }
];

export function districtLabel(value: SriLankanDistrict | '' | undefined): string {
  return DISTRICTS.find((district) => district.value === value)?.label ?? '';
}

/** `Colombo 03, Colombo`, or just `Kandy` when the city and district share a name. */
export function placeLabel(city: string | undefined, district: SriLankanDistrict | '' | undefined) {
  const label = districtLabel(district);
  if (!city?.trim()) return label;
  return city.trim().toLowerCase() === label.toLowerCase()
    ? city.trim()
    : `${city.trim()}, ${label}`;
}

/** Keeps the 9 subscriber digits; a leading 0 or 94 typed out of habit is dropped. */
export function normalisePhoneDigits(input: string): string {
  let digits = input.replace(/\D/g, '');
  if (digits.startsWith('94') && digits.length > 9) digits = digits.slice(2);
  if (digits.startsWith('0')) digits = digits.slice(1);
  return digits.slice(0, 9);
}

/** Backend format: `+94` followed by 9 digits (`ValidationPatterns.PHONE`). */
export const toE164 = (digits: string) => `+94${digits}`;

const toNumber = (value: string) => Number(value.trim());
const optionalText = (value: string) => (value.trim() ? value.trim() : undefined);

export function toFeeEstimateRequest(form: BookingForm): FeeEstimateRequest {
  return {
    pickupDistrict: form.pickupDistrict as SriLankanDistrict,
    deliveryDistrict: form.deliveryDistrict as SriLankanDistrict,
    actualWeightKg: toNumber(form.actualWeightKg),
    lengthCm: toNumber(form.lengthCm),
    widthCm: toNumber(form.widthCm),
    heightCm: toNumber(form.heightCm),
    speed: form.speed
  };
}

export function toCreateOrderRequest(form: BookingForm): CreateOrderRequest {
  return {
    pickupContactName: form.pickupContactName.trim(),
    pickupContactPhone: toE164(form.pickupContactPhone),
    pickupAddressLine1: form.pickupAddressLine1.trim(),
    pickupAddressLine2: optionalText(form.pickupAddressLine2),
    pickupCity: form.pickupCity.trim(),
    pickupDistrict: form.pickupDistrict as SriLankanDistrict,
    pickupPostalCode: form.pickupPostalCode,
    recipientName: form.recipientName.trim(),
    recipientPhone: toE164(form.recipientPhone),
    recipientEmail: form.recipientEmail.trim(),
    deliveryAddressLine1: form.deliveryAddressLine1.trim(),
    deliveryAddressLine2: optionalText(form.deliveryAddressLine2),
    deliveryCity: form.deliveryCity.trim(),
    deliveryDistrict: form.deliveryDistrict as SriLankanDistrict,
    deliveryPostalCode: form.deliveryPostalCode,
    lengthCm: toNumber(form.lengthCm),
    widthCm: toNumber(form.widthCm),
    heightCm: toNumber(form.heightCm),
    actualWeightKg: toNumber(form.actualWeightKg),
    speed: form.speed,
    itemDescription: optionalText(form.itemDescription),
    declaredValue: form.declaredValue.trim() ? toNumber(form.declaredValue) : 0,
    codAmount: form.collectCash && form.codAmount.trim() ? toNumber(form.codAmount) : 0,
    prohibitedItemsDeclared: form.prohibitedItemsDeclared
  };
}
