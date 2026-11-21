import { initialBookingForm, type BookingForm } from './bookingForm';
import type { BookingStepId } from './validation';

/**
 * An unfinished booking kept in this browser, so a refresh, a dropped connection or a closed tab does
 * not lose what the customer typed. It is stored per account and removed on sign-out, after a booking
 * is placed, and after {@link DRAFT_TTL_MS}: a shared phone never shows another person's addresses.
 */
export interface BookingDraft {
  version: 1;
  savedAt: number;
  form: BookingForm;
  step: BookingStepId;
  completed: BookingStepId[];
  /** Sent with the order, so pressing Confirm again after a lost response cannot book twice. */
  clientRequestId: string;
  /** Per side: the saved address it was filled from, so it is not offered for saving again. */
  selectedSaved?: { pickup: string | null; delivery: string | null };
  /** Per side: whether to save the typed address after booking, and its name. */
  saveChoice?: {
    pickup: { save: boolean; label: string };
    delivery: { save: boolean; label: string };
  };
}

/** Every draft key starts with this; sign-out removes them all (see useAuthStore). */
export const DRAFT_KEY_PREFIX = 'swiftdrop-booking-draft:';
export const DRAFT_TTL_MS = 7 * 24 * 60 * 60 * 1000;

const keyFor = (userId: string) => DRAFT_KEY_PREFIX + userId;
const STEPS: BookingStepId[] = ['pickup', 'dropoff', 'parcel', 'review'];

/** True when the customer has typed nothing worth keeping. */
export function isEmptyBooking(form: BookingForm): boolean {
  return (Object.keys(initialBookingForm) as (keyof BookingForm)[]).every(
    (field) => form[field] === initialBookingForm[field]
  );
}

export function loadDraft(userId: string, now = Date.now()): BookingDraft | null {
  try {
    const raw = window.localStorage.getItem(keyFor(userId));
    if (!raw) return null;
    const draft = JSON.parse(raw) as Partial<BookingDraft>;
    const valid =
      draft.version === 1 &&
      typeof draft.savedAt === 'number' &&
      now - draft.savedAt < DRAFT_TTL_MS &&
      typeof draft.clientRequestId === 'string' &&
      STEPS.includes(draft.step as BookingStepId) &&
      draft.form !== null &&
      typeof draft.form === 'object';
    if (!valid) {
      window.localStorage.removeItem(keyFor(userId));
      return null;
    }
    // Fields added to the form later start empty instead of undefined.
    return {
      ...(draft as BookingDraft),
      form: { ...initialBookingForm, ...draft.form },
      completed: (draft.completed ?? []).filter((s) => STEPS.includes(s))
    };
  } catch {
    // Storage blocked (private mode) or a corrupt value: start a fresh booking.
    return null;
  }
}

export function saveDraft(
  userId: string,
  draft: Omit<BookingDraft, 'version' | 'savedAt'>,
  now = Date.now()
) {
  try {
    if (isEmptyBooking(draft.form)) {
      window.localStorage.removeItem(keyFor(userId));
      return;
    }
    const value: BookingDraft = { version: 1, savedAt: now, ...draft };
    window.localStorage.setItem(keyFor(userId), JSON.stringify(value));
  } catch {
    // Quota or blocked storage: the booking still works, it just is not kept.
  }
}

export function clearDraft(userId: string) {
  try {
    window.localStorage.removeItem(keyFor(userId));
  } catch {
    // Nothing to clear.
  }
}

/** Removes every account's draft from this browser. Called on sign-out. */
export function clearAllDrafts() {
  try {
    const keys: string[] = [];
    for (let i = 0; i < window.localStorage.length; i++) {
      const key = window.localStorage.key(i);
      if (key?.startsWith(DRAFT_KEY_PREFIX)) keys.push(key);
    }
    keys.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // Storage blocked: there are no drafts either.
  }
}

/** A fresh ID for a new booking; crypto.randomUUID is available in every browser the app supports. */
export function newClientRequestId(): string {
  return crypto.randomUUID();
}
