import type {
  MerchantApprovalResponse,
  RegisterMerchantRequest,
  SriLankanDistrict
} from '@/services/api/merchantApi';
import {
  normalisePhoneDigits,
  provinceOf,
  type ProvinceId
} from '@/features/customer/components/booking/bookingForm';

export type BusinessCategory = RegisterMerchantRequest['businessCategory'];
export type MonthlyParcelVolume = RegisterMerchantRequest['monthlyParcelVolume'];

export const BUSINESS_CATEGORIES: { value: BusinessCategory; label: string }[] = [
  { value: 'FASHION_AND_CLOTHING', label: 'Fashion and clothing' },
  { value: 'ELECTRONICS', label: 'Electronics' },
  { value: 'HOME_AND_LIVING', label: 'Home and living' },
  { value: 'HEALTH_AND_BEAUTY', label: 'Health and beauty' },
  { value: 'FOOD_AND_GROCERY', label: 'Food and grocery' },
  { value: 'BOOKS_AND_STATIONERY', label: 'Books and stationery' },
  { value: 'GIFTS_AND_CRAFTS', label: 'Gifts and crafts' },
  { value: 'OTHER', label: 'Other' }
];

export const MONTHLY_VOLUMES: { value: MonthlyParcelVolume; label: string }[] = [
  { value: 'UP_TO_50', label: 'Up to 50' },
  { value: 'FROM_51_TO_200', label: '51 to 200' },
  { value: 'FROM_201_TO_1000', label: '201 to 1,000' },
  { value: 'OVER_1000', label: 'More than 1,000' }
];

export const categoryLabel = (value?: string | null) =>
  BUSINESS_CATEGORIES.find((c) => c.value === value)?.label ?? '—';
export const volumeLabel = (value?: string | null) =>
  MONTHLY_VOLUMES.find((v) => v.value === value)?.label ?? '—';

/** Every field as the form holds it: text, with '' for "not chosen yet". Phone is the 9 digits after +94. */
export interface ApplicationForm {
  businessName: string;
  businessCategory: BusinessCategory | '';
  monthlyParcelVolume: MonthlyParcelVolume | '';
  businessRegNo: string;
  websiteUrl: string;
  contactEmail: string;
  contactPhone: string;
  pickupAddress: string;
  pickupCity: string;
  pickupPostalCode: string;
  /** Only narrows the district list; the request carries the district. */
  province: ProvinceId | '';
  operatingDistrict: SriLankanDistrict | '';
}

export type ApplicationField = keyof ApplicationForm;
export type ApplicationErrors = Partial<Record<ApplicationField, string>>;

export const FIELD_ORDER: ApplicationField[] = [
  'businessName',
  'businessCategory',
  'monthlyParcelVolume',
  'businessRegNo',
  'websiteUrl',
  'contactEmail',
  'contactPhone',
  'pickupAddress',
  'pickupCity',
  'pickupPostalCode',
  'province',
  'operatingDistrict'
];

/** Same rules as the backend's RegisterMerchantRequest. */
const PATTERNS = {
  businessName: /^[\p{L}\p{M}\p{N} .,&'’()/+#!@-]*$/u,
  regNo: /^[A-Za-z0-9 ./-]*$/,
  url: /^(https?:\/\/)?[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+(:\d+)?(\/[^\s<>"]*)?$/i,
  email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  phoneDigits: /^[1-9]\d{8}$/,
  address: /^[\p{L}\p{M}\p{N} .,/#()&'’:-]*$/u,
  city: /^[\p{L}\p{M}\p{N} .'’-]*$/u,
  postalCode: /^\d{5}$/
};

const NOT_ALLOWED = 'Remove characters such as < > " ; =';
export const PROOF_REQUIRED = 'Add a registration number or a website or social media link';

export function emptyApplication(
  email = '',
  district: SriLankanDistrict | '' = ''
): ApplicationForm {
  return {
    businessName: '',
    businessCategory: '',
    monthlyParcelVolume: '',
    businessRegNo: '',
    websiteUrl: '',
    contactEmail: email,
    contactPhone: '',
    pickupAddress: '',
    pickupCity: '',
    pickupPostalCode: '',
    province: provinceOf(district || undefined),
    operatingDistrict: district
  };
}

/** A rejected application, back in the form so the business can correct it. */
export function fromApplication(application: MerchantApprovalResponse): ApplicationForm {
  return {
    businessName: application.businessName ?? '',
    businessCategory: application.businessCategory ?? '',
    monthlyParcelVolume: application.monthlyParcelVolume ?? '',
    businessRegNo: application.businessRegNo ?? '',
    websiteUrl: application.websiteUrl ?? '',
    contactEmail: application.contactEmail ?? '',
    contactPhone: normalisePhoneDigits(application.contactPhone ?? ''),
    pickupAddress: application.pickupAddress ?? '',
    pickupCity: application.pickupCity ?? '',
    pickupPostalCode: application.pickupPostalCode ?? '',
    province: provinceOf(application.operatingDistrict ?? undefined),
    operatingDistrict: application.operatingDistrict ?? ''
  };
}

function text(value: string, label: string, min: number, max: number, pattern: RegExp) {
  const v = value.trim();
  if (!v) return `Enter the ${label}`;
  if (v.length < min) return `The ${label} needs at least ${min} characters`;
  if (v.length > max) return `Keep the ${label} under ${max + 1} characters`;
  return pattern.test(v) ? undefined : NOT_ALLOWED;
}

export function validateField(field: ApplicationField, form: ApplicationForm): string | undefined {
  const value = form[field].trim();
  const noProof = !form.businessRegNo.trim() && !form.websiteUrl.trim();
  switch (field) {
    case 'businessName':
      return text(value, 'business name', 2, 150, PATTERNS.businessName);
    case 'businessCategory':
      return value ? undefined : 'Choose what you sell';
    case 'monthlyParcelVolume':
      return value ? undefined : 'Choose how many parcels you send a month';
    case 'businessRegNo':
      if (!value) return undefined;
      if (value.length > 64) return 'Keep it under 65 characters';
      return PATTERNS.regNo.test(value) ? undefined : 'Use letters, numbers, spaces, / . or - only';
    case 'websiteUrl':
      if (!value) return noProof ? PROOF_REQUIRED : undefined;
      if (value.length > 255) return 'Keep the link under 256 characters';
      return PATTERNS.url.test(value) ? undefined : 'Enter a link such as facebook.com/yourshop';
    case 'contactEmail':
      if (!value) return 'Enter the business email';
      return PATTERNS.email.test(value) && value.length <= 100
        ? undefined
        : 'Enter a valid email address';
    case 'contactPhone':
      if (!value) return 'Enter the business phone';
      return PATTERNS.phoneDigits.test(value) ? undefined : 'Enter the 9 digits after +94';
    case 'pickupAddress':
      return text(value, 'pickup address', 5, 255, PATTERNS.address);
    case 'pickupCity':
      return text(value, 'city', 2, 100, PATTERNS.city);
    case 'pickupPostalCode':
      if (!value) return 'Enter the 5-digit postal code';
      return PATTERNS.postalCode.test(value) ? undefined : 'Postal codes have 5 digits, e.g. 10250';
    case 'province':
      return value || form.operatingDistrict ? undefined : 'Choose the pickup province';
    case 'operatingDistrict':
      return value ? undefined : 'Choose the pickup district';
  }
}

export function validateApplication(form: ApplicationForm): ApplicationErrors {
  const errors: ApplicationErrors = {};
  for (const field of FIELD_ORDER) {
    const error = validateField(field, form);
    if (error) errors[field] = error;
  }
  return errors;
}

/** The request body. Call only after {@link validateApplication} found no errors. */
export function toRequest(form: ApplicationForm): RegisterMerchantRequest {
  const optional = (value: string) => (value.trim() ? value.trim() : undefined);
  return {
    businessName: form.businessName.trim(),
    businessCategory: form.businessCategory as BusinessCategory,
    monthlyParcelVolume: form.monthlyParcelVolume as MonthlyParcelVolume,
    businessRegNo: optional(form.businessRegNo),
    websiteUrl: optional(form.websiteUrl),
    contactEmail: form.contactEmail.trim(),
    contactPhone: `+94${form.contactPhone}`,
    pickupAddress: form.pickupAddress.trim(),
    pickupCity: form.pickupCity.trim(),
    pickupPostalCode: form.pickupPostalCode.trim(),
    operatingDistrict: form.operatingDistrict as SriLankanDistrict
  };
}
