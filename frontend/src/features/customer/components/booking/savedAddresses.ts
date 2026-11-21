import type { SavedAddress, SavedAddressRequest } from '@/services/api/orderApi';
import {
  normalisePhoneDigits,
  provinceOf,
  toE164,
  type BookingField,
  type BookingForm
} from './bookingForm';

export type AddressSide = 'pickup' | 'delivery';

/** The booking fields one side of the address book fills: contact, then the address parts. */
export function sideFields(side: AddressSide): BookingField[] {
  const contact: BookingField[] =
    side === 'pickup'
      ? ['pickupContactName', 'pickupContactPhone', 'pickupContactAlternatePhone']
      : ['recipientName', 'recipientPhone', 'recipientAlternatePhone'];
  const parts = [
    'House',
    'Street',
    'Area',
    'Landmark',
    'Province',
    'City',
    'District',
    'PostalCode'
  ];
  return [...contact, ...parts.map((part) => `${side}${part}` as BookingField)];
}

/** Fills one side of the booking from a saved address. The recipient's email is not saved, so it stays. */
export function applySavedAddress(
  form: BookingForm,
  side: AddressSide,
  a: SavedAddress
): BookingForm {
  const contact =
    side === 'pickup'
      ? {
          pickupContactName: a.contactName ?? '',
          pickupContactPhone: normalisePhoneDigits(a.contactPhone ?? ''),
          pickupContactAlternatePhone: normalisePhoneDigits(a.alternatePhone ?? '')
        }
      : {
          recipientName: a.contactName ?? '',
          recipientPhone: normalisePhoneDigits(a.contactPhone ?? ''),
          recipientAlternatePhone: normalisePhoneDigits(a.alternatePhone ?? '')
        };
  return {
    ...form,
    ...contact,
    [`${side}House`]: a.house ?? '',
    [`${side}Street`]: a.street ?? '',
    [`${side}Area`]: a.area ?? '',
    [`${side}Landmark`]: a.landmark ?? '',
    [`${side}City`]: a.city ?? '',
    [`${side}District`]: a.district ?? '',
    [`${side}Province`]: provinceOf(a.district ?? ''),
    [`${side}PostalCode`]: a.postalCode ?? ''
  };
}

/** One side of a (valid) booking as an address-book entry. */
export function toSavedAddressRequest(
  form: BookingForm,
  side: AddressSide,
  label: string
): SavedAddressRequest {
  const name = side === 'pickup' ? form.pickupContactName : form.recipientName;
  const phone = side === 'pickup' ? form.pickupContactPhone : form.recipientPhone;
  const alternate =
    side === 'pickup' ? form.pickupContactAlternatePhone : form.recipientAlternatePhone;
  const optional = (value: string) => (value.trim() ? value.trim() : undefined);
  return {
    label: label.trim(),
    contactName: name.trim(),
    contactPhone: toE164(phone),
    alternatePhone: alternate ? toE164(alternate) : undefined,
    house: form[`${side}House`].trim(),
    street: form[`${side}Street`].trim(),
    area: optional(form[`${side}Area`]),
    landmark: optional(form[`${side}Landmark`]),
    city: form[`${side}City`].trim(),
    district: form[`${side}District`] as SavedAddressRequest['district'],
    postalCode: form[`${side}PostalCode`]
  };
}

/** "45/2, Temple Road, Nugegoda 10250" for the picker. */
export function addressSummary(a: SavedAddress): string {
  return (
    [[a.house, a.street].filter(Boolean).join(', '), a.city].filter(Boolean).join(', ') +
    (a.postalCode ? ` ${a.postalCode}` : '')
  );
}

const LABEL_PATTERN = /^[\p{L}\p{M}\p{N} .'’&/-]*$/u;

/** The name the customer gives a saved address, checked like the backend's label rule. */
export function validateAddressLabel(label: string): string | undefined {
  const value = label.trim();
  if (!value) return 'Give this address a name, such as Home or Office';
  if (value.length < 2) return 'Use at least 2 characters';
  if (value.length > 40) return 'Use 40 characters or fewer';
  return LABEL_PATTERN.test(value) ? undefined : 'Use letters, numbers and spaces only';
}
