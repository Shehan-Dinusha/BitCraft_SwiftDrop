import { describe, expect, it } from 'vitest';
import { passwordStrength, validatePassword } from './passwordPolicy';

describe('validatePassword', () => {
  it('accepts a password that meets every rule', () => {
    expect(validatePassword('SwiftDrop123')).toBeNull();
  });

  it.each([
    ['Ab1', 'at least 8'],
    ['a'.repeat(72) + '1', 'not exceed 72'],
    ['OnlyLetters', 'one letter and one digit'],
    ['1234567890', 'one letter and one digit'],
    ['Password123', 'too common']
  ])('rejects %s', (password, message) => {
    expect(validatePassword(password)).toContain(message);
  });

  it('rejects a password containing the username or email local part', () => {
    expect(validatePassword('kasun2026!', { username: 'Kasun' })).toContain('username');
    expect(validatePassword('nimal2026!', { email: 'nimal@example.com' })).toContain('email');
  });

  it('ignores identity fragments shorter than 3 characters', () => {
    expect(validatePassword('ab2026xyz!', { username: 'ab' })).toBeNull();
  });
});

describe('passwordStrength', () => {
  it('scores an empty password as 0 with no label', () => {
    expect(passwordStrength('')).toEqual({ score: 0, label: '', hints: [] });
  });

  it('scores a rejected password as 1 and explains why', () => {
    const result = passwordStrength('short1');
    expect(result.score).toBe(1);
    expect(result.label).toBe('Too weak');
    expect(result.hints[0]).toContain('at least 8');
  });

  it('grades accepted passwords from fair to strong', () => {
    expect(passwordStrength('swiftdrop1').label).toBe('Fair');
    expect(passwordStrength('SwiftDrop2026x').label).toBe('Good');
    expect(passwordStrength('Swift!Drop#2026x').label).toBe('Strong');
  });

  it('gives no hints once the password is strong', () => {
    expect(passwordStrength('Swift!Drop#2026x').hints).toEqual([]);
  });
});
