import {
  maxLength,
  minLength,
  pattern,
  PathKind,
  SchemaPath,
  SchemaPathRules,
} from '@angular/forms/signals';

export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 128;

/** At least one letter and one digit, in any alphabet (Unicode-aware). */
export const PASSWORD_LETTER_AND_DIGIT = /^(?=.*\p{L})(?=.*\p{N})[\s\S]*$/u;

/**
 * Mirrors the backend `@StrongPassword` rules for instant feedback in every form that sets a
 * password. The backend stays the authority: it additionally rejects well-known passwords and
 * trivial repetition, which surface as a 400 VALIDATION_FAILED.
 *
 * Error kinds: `minLength`, `maxLength`, `pattern` (letter/digit missing).
 */
export function passwordPolicy<TPathKind extends PathKind = PathKind.Root>(
  path: SchemaPath<string, SchemaPathRules.Supported, TPathKind>,
): void {
  minLength(path, PASSWORD_MIN_LENGTH);
  maxLength(path, PASSWORD_MAX_LENGTH);
  pattern(path, PASSWORD_LETTER_AND_DIGIT);
}