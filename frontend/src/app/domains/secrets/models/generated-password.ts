/** Strength label of a generated password; same thresholds as the backend (40 / 60 / 80 bits). */
export type PasswordStrength = 'WEAK' | 'FAIR' | 'STRONG' | 'VERY_STRONG';

/** Shape of the backend's `GeneratedPasswordResponse`. */
export interface GeneratedPassword {
  readonly password: string;
  readonly entropyBits: number;
  readonly strength: PasswordStrength;
}

export type CharacterSet = 'lowercase' | 'uppercase' | 'digits' | 'symbols';

export interface PasswordOptions {
  readonly length: number;
  readonly lowercase: boolean;
  readonly uppercase: boolean;
  readonly digits: boolean;
  readonly symbols: boolean;
}

/** Same limits and defaults as the backend's `PasswordGenerator`. */
export const GENERATOR_MIN_LENGTH = 8;
export const GENERATOR_MAX_LENGTH = 128;

export const DEFAULT_PASSWORD_OPTIONS: PasswordOptions = {
  length: 20,
  lowercase: true,
  uppercase: true,
  digits: true,
  symbols: true,
};
