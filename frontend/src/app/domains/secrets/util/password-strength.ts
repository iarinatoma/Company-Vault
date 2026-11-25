import {
  DEFAULT_PASSWORD_OPTIONS,
  GENERATOR_MAX_LENGTH,
  GENERATOR_MIN_LENGTH,
  PasswordStrength,
} from '@app/secrets/models/generated-password';

/** 1 (weak) to 4 (very strong): how many segments of the strength meter are filled. */
export function strengthLevel(strength: PasswordStrength): number {
  switch (strength) {
    case 'WEAK':
      return 1;
    case 'FAIR':
      return 2;
    case 'STRONG':
      return 3;
    default:
      return 4;
  }
}

export function strengthLabel(strength: PasswordStrength): string {
  switch (strength) {
    case 'WEAK':
      return $localize`:@@generator.weak:Weak`;
    case 'FAIR':
      return $localize`:@@generator.fair:Fair`;
    case 'STRONG':
      return $localize`:@@generator.strong:Strong`;
    default:
      return $localize`:@@generator.veryStrong:Very strong`;
  }
}

/** Colour of the filled segments of the strength meter. */
export function strengthColor(strength: PasswordStrength): string {
  switch (strength) {
    case 'WEAK':
      return 'bg-red-500';
    case 'FAIR':
      return 'bg-amber-500';
    case 'STRONG':
      return 'bg-lime-500';
    default:
      return 'bg-emerald-600';
  }
}

/** A whole length within the backend's limits; anything unreadable falls back to the default. */
export function clampLength(raw: number): number {
  if (!Number.isFinite(raw)) return DEFAULT_PASSWORD_OPTIONS.length;
  return Math.min(GENERATOR_MAX_LENGTH, Math.max(GENERATOR_MIN_LENGTH, Math.round(raw)));
}
