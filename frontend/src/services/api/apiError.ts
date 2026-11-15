import axios from 'axios';

export type ParsedApiError = {
  status?: number;
  /** Machine-readable code (the backend puts e.g. EMAIL_NOT_VERIFIED in the `error` field). */
  code?: string;
  /** Best human-readable message: field errors first, then the server message. */
  message?: string;
};

type ErrorBody = { message?: string; error?: string; fieldErrors?: Record<string, string[]> };

/** Normalises an axios failure from the SwiftDrop API; returns `{}` for non-HTTP errors. */
export function parseApiError(error: unknown): ParsedApiError {
  if (!axios.isAxiosError(error)) return {};
  const data = error.response?.data as ErrorBody | undefined;
  const details = Object.values(data?.fieldErrors ?? {})
    .flat()
    .join(' ');
  return { status: error.response?.status, code: data?.error, message: details || data?.message };
}

/** Error code returned by `/login` when the password was right but the email is not verified. */
export const EMAIL_NOT_VERIFIED = 'EMAIL_NOT_VERIFIED';
