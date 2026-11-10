import { isAxiosError } from 'axios';

/**
 * Human-readable message for a failed API call. Prefers the backend's ApiErrorResponse `message`
 * (e.g. "Unsupported event subscription(s): parcel.#") over Axios' generic
 * "Request failed with status code 400".
 */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (isAxiosError(err)) {
    const message = (err.response?.data as { message?: unknown } | undefined)?.message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }
  return err instanceof Error && err.message ? err.message : fallback;
}
