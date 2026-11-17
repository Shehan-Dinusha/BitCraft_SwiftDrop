import { describe, expect, it } from 'vitest';
import { isMfaChallenge, type LoginResult } from './authApi';

describe('isMfaChallenge', () => {
  it('recognises a second-factor challenge from /login', () => {
    const challenge: LoginResult = { mfaRequired: true, challengeId: 'abc', expiresInMinutes: 10 };
    expect(isMfaChallenge(challenge)).toBe(true);
  });

  it('treats a token response as a completed sign-in', () => {
    const tokens: LoginResult = { accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600 };
    expect(isMfaChallenge(tokens)).toBe(false);
  });
});
