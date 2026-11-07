import { Field, Textarea } from '@/design-system';
import { normalisePhoneDigits } from './bookingForm';
import { AddressFields, type BookingStepProps } from './AddressFields';

export function DropoffStep(props: BookingStepProps) {
  const { form, errors, onChange, onBlur } = props;

  return (
    <fieldset className="flex min-w-0 flex-col gap-4">
      <legend className="mb-1">
        <h2 className="text-h3 text-primary">Where is it going?</h2>
      </legend>
      <p className="text-body text-secondary">Tell us who receives the parcel.</p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Field
          label="Recipient name"
          required
          autoComplete="section-delivery name"
          maxLength={100}
          value={form.recipientName}
          error={errors.recipientName}
          onChange={(event) => onChange('recipientName', event.target.value)}
          onBlur={() => onBlur('recipientName')}
        />
        <Field
          label="Recipient phone"
          required
          variant="phone"
          autoComplete="section-delivery tel-national"
          inputMode="numeric"
          value={form.recipientPhone}
          error={errors.recipientPhone}
          hint="9 digits after +94"
          onChange={(event) => onChange('recipientPhone', normalisePhoneDigits(event.target.value))}
          onBlur={() => onBlur('recipientPhone')}
        />
        <Field
          label="Second phone"
          variant="phone"
          autoComplete="section-delivery tel-national"
          inputMode="numeric"
          value={form.recipientAlternatePhone}
          error={errors.recipientAlternatePhone}
          hint="Optional. Another number the courier can try"
          onChange={(event) =>
            onChange('recipientAlternatePhone', normalisePhoneDigits(event.target.value))
          }
          onBlur={() => onBlur('recipientAlternatePhone')}
        />
        <Field
          label="Recipient email"
          required
          inputMode="email"
          autoComplete="section-delivery email"
          maxLength={128}
          value={form.recipientEmail}
          error={errors.recipientEmail}
          hint="Used for delivery notifications"
          onChange={(event) => onChange('recipientEmail', event.target.value)}
          onBlur={() => onBlur('recipientEmail')}
        />
      </div>

      <AddressFields side="delivery" {...props} />

      <Textarea
        label="Delivery instructions"
        rows={3}
        maxLength={500}
        value={form.customerNotes}
        error={errors.customerNotes}
        helper="Optional. For example a gate code or a landmark. Do not write passwords."
        onChange={(event) => onChange('customerNotes', event.target.value)}
        onBlur={() => onBlur('customerNotes')}
      />
    </fieldset>
  );
}
