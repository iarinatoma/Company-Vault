import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { form, schema } from '@angular/forms/signals';
import { passwordPolicy } from './password-policy';

function errorsFor(password: string): string[] {
  return TestBed.runInInjectionContext(() => {
    const f = form(
      signal({ password }),
      schema<{ password: string }>((path) => passwordPolicy(path.password)),
    );
    return f.password().errors().map((e) => e.kind);
  });
}

describe('passwordPolicy', () => {
  it('accepts a password with 12+ characters, a letter and a digit', () => {
    expect(errorsFor('brand-new-password-1')).toEqual([]);
  });

  it('rejects passwords shorter than 12 characters', () => {
    expect(errorsFor('short-pw-1')).toContain('minLength');
  });

  it('rejects passwords longer than 128 characters', () => {
    expect(errorsFor('a1'.repeat(65))).toContain('maxLength');
  });

  it('requires at least one letter and one digit', () => {
    expect(errorsFor('only-letters-here')).toContain('pattern');
    expect(errorsFor('1234567890123')).toContain('pattern');
  });

  it('accepts letters from any alphabet', () => {
    expect(errorsFor('parolă-sigură-2026')).toEqual([]);
  });
});