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
  /** Optional second number for the sender: the 9 digits after `+94`, or empty. */
  pickupContactAlternatePhone: string;
  pickupHouse: string;
  pickupStreet: string;
  pickupArea: string;
  pickupLandmark: string;
  /** Only narrows the district list; the order stores the district. */
  pickupProvince: ProvinceId | '';
  pickupCity: string;
  pickupDistrict: SriLankanDistrict | '';
  pickupPostalCode: string;
  recipientName: string;
  recipientPhone: string;
  /** Optional second number for the recipient: the 9 digits after `+94`, or empty. */
  recipientAlternatePhone: string;
  recipientEmail: string;
  deliveryHouse: string;
  deliveryStreet: string;
  deliveryArea: string;
  deliveryLandmark: string;
  deliveryProvince: ProvinceId | '';
  deliveryCity: string;
  deliveryDistrict: SriLankanDistrict | '';
  deliveryPostalCode: string;
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  actualWeightKg: string;
  speed: OrderSpeed;
  itemDescription: string;
  /** Optional notes for the courier, such as a gate code or a landmark. */
  customerNotes: string;
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
  pickupContactAlternatePhone: '',
  pickupHouse: '',
  pickupStreet: '',
  pickupArea: '',
  pickupLandmark: '',
  pickupProvince: '',
  pickupCity: '',
  pickupDistrict: '',
  pickupPostalCode: '',
  recipientName: '',
  recipientPhone: '',
  recipientAlternatePhone: '',
  recipientEmail: '',
  deliveryHouse: '',
  deliveryStreet: '',
  deliveryArea: '',
  deliveryLandmark: '',
  deliveryProvince: '',
  deliveryCity: '',
  deliveryDistrict: '',
  deliveryPostalCode: '',
  lengthCm: '',
  widthCm: '',
  heightCm: '',
  actualWeightKg: '',
  speed: 'STANDARD',
  itemDescription: '',
  customerNotes: '',
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

/** The nine provinces and their districts (fixed administrative map). */
export type ProvinceId =
  | 'WESTERN'
  | 'CENTRAL'
  | 'SOUTHERN'
  | 'NORTHERN'
  | 'EASTERN'
  | 'NORTH_WESTERN'
  | 'NORTH_CENTRAL'
  | 'UVA'
  | 'SABARAGAMUWA';

export const PROVINCES: { id: ProvinceId; label: string; districts: SriLankanDistrict[] }[] = [
  { id: 'WESTERN', label: 'Western', districts: ['COLOMBO', 'GAMPAHA', 'KALUTARA'] },
  { id: 'CENTRAL', label: 'Central', districts: ['KANDY', 'MATALE', 'NUWARA_ELIYA'] },
  { id: 'SOUTHERN', label: 'Southern', districts: ['GALLE', 'MATARA', 'HAMBANTOTA'] },
  {
    id: 'NORTHERN',
    label: 'Northern',
    districts: ['JAFFNA', 'KILINOCHCHI', 'MANNAR', 'VAVUNIYA', 'MULLAITIVU']
  },
  { id: 'EASTERN', label: 'Eastern', districts: ['BATTICALOA', 'AMPARA', 'TRINCOMALEE'] },
  { id: 'NORTH_WESTERN', label: 'North Western', districts: ['KURUNEGALA', 'PUTTALAM'] },
  { id: 'NORTH_CENTRAL', label: 'North Central', districts: ['ANURADHAPURA', 'POLONNARUWA'] },
  { id: 'UVA', label: 'Uva', districts: ['BADULLA', 'MONARAGALA'] },
  { id: 'SABARAGAMUWA', label: 'Sabaragamuwa', districts: ['RATNAPURA', 'KEGALLE'] }
];

export function provinceOf(district: SriLankanDistrict | '' | undefined): ProvinceId | '' {
  return PROVINCES.find((province) => district && province.districts.includes(district))?.id ?? '';
}

/**
 * The order stores one street line and one optional extra line. The form collects house, street,
 * area and landmark separately and joins them here ("No. 45, Galle Road" / "Kollupitiya, Opposite
 * Liberty Plaza"). The length limits in `validation.ts` keep the joined text within 200 characters.
 */
export const addressLine1 = (house: string, street: string) => `${house.trim()}, ${street.trim()}`;
export const addressLine2 = (area: string, landmark: string) => {
  const parts = [area.trim(), landmark.trim()].filter(Boolean);
  return parts.length ? parts.join(', ') : undefined;
};

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
    pickupContactAlternatePhone: form.pickupContactAlternatePhone
      ? toE164(form.pickupContactAlternatePhone)
      : undefined,
    pickupAddressLine1: addressLine1(form.pickupHouse, form.pickupStreet),
    pickupAddressLine2: addressLine2(form.pickupArea, form.pickupLandmark),
    pickupCity: form.pickupCity.trim(),
    pickupDistrict: form.pickupDistrict as SriLankanDistrict,
    pickupPostalCode: form.pickupPostalCode,
    recipientName: form.recipientName.trim(),
    recipientPhone: toE164(form.recipientPhone),
    recipientAlternatePhone: form.recipientAlternatePhone
      ? toE164(form.recipientAlternatePhone)
      : undefined,
    recipientEmail: form.recipientEmail.trim(),
    deliveryAddressLine1: addressLine1(form.deliveryHouse, form.deliveryStreet),
    deliveryAddressLine2: addressLine2(form.deliveryArea, form.deliveryLandmark),
    deliveryCity: form.deliveryCity.trim(),
    deliveryDistrict: form.deliveryDistrict as SriLankanDistrict,
    deliveryPostalCode: form.deliveryPostalCode,
    lengthCm: toNumber(form.lengthCm),
    widthCm: toNumber(form.widthCm),
    heightCm: toNumber(form.heightCm),
    actualWeightKg: toNumber(form.actualWeightKg),
    speed: form.speed,
    itemDescription: optionalText(form.itemDescription),
    customerNotes: optionalText(form.customerNotes),
    declaredValue: form.declaredValue.trim() ? toNumber(form.declaredValue) : 0,
    codAmount: form.collectCash && form.codAmount.trim() ? toNumber(form.codAmount) : 0,
    prohibitedItemsDeclared: form.prohibitedItemsDeclared
  };
}
