import { afterEach, describe, expect, it } from 'vitest';
import { initialBookingForm } from './bookingForm';
import {
  clearAllDrafts,
  DRAFT_KEY_PREFIX,
  DRAFT_TTL_MS,
  isEmptyBooking,
  loadDraft,
  saveDraft
} from './bookingDraft';

const typed = { ...initialBookingForm, pickupContactName: 'Nimal Perera', pickupCity: 'Nugegoda' };
const draft = { form: typed, step: 'dropoff' as const, completed: ['pickup' as const], clientRequestId: 'req-1' };

afterEach(() => window.localStorage.clear());

describe('booking drafts', () => {
  it('keeps an unfinished booking per account and restores it', () => {
    saveDraft('user-a', draft, 1_000);
    const restored = loadDraft('user-a', 2_000);
    expect(restored?.form.pickupContactName).toBe('Nimal Perera');
    expect(restored?.step).toBe('dropoff');
    expect(restored?.clientRequestId).toBe('req-1');
    expect(loadDraft('user-b', 2_000)).toBeNull();
  });

  it('does not keep an empty booking', () => {
    expect(isEmptyBooking(initialBookingForm)).toBe(true);
    saveDraft('user-a', { ...draft, form: initialBookingForm });
    expect(window.localStorage.getItem(DRAFT_KEY_PREFIX + 'user-a')).toBeNull();
  });

  it('drops a draft older than seven days or one that is corrupt', () => {
    saveDraft('user-a', draft, 0);
    expect(loadDraft('user-a', DRAFT_TTL_MS + 1)).toBeNull();
    window.localStorage.setItem(DRAFT_KEY_PREFIX + 'user-b', '{not json');
    expect(loadDraft('user-b')).toBeNull();
  });

  it('sign-out removes every account draft and nothing else', () => {
    saveDraft('user-a', draft);
    saveDraft('user-b', draft);
    window.localStorage.setItem('other-setting', 'kept');
    clearAllDrafts();
    expect(loadDraft('user-a')).toBeNull();
    expect(loadDraft('user-b')).toBeNull();
    expect(window.localStorage.getItem('other-setting')).toBe('kept');
  });
});
