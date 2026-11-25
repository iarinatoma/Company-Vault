import { clampLength, strengthColor, strengthLabel, strengthLevel } from './password-strength';

describe('password strength', () => {
  it('fills more of the meter the stronger the password is', () => {
    expect(strengthLevel('WEAK')).toBe(1);
    expect(strengthLevel('FAIR')).toBe(2);
    expect(strengthLevel('STRONG')).toBe(3);
    expect(strengthLevel('VERY_STRONG')).toBe(4);
  });

  it('names every strength', () => {
    expect(strengthLabel('WEAK')).toBe('Weak');
    expect(strengthLabel('FAIR')).toBe('Fair');
    expect(strengthLabel('STRONG')).toBe('Strong');
    expect(strengthLabel('VERY_STRONG')).toBe('Very strong');
  });

  it('gives every strength its own colour', () => {
    const colors = new Set(
      (['WEAK', 'FAIR', 'STRONG', 'VERY_STRONG'] as const).map((strength) => strengthColor(strength)),
    );

    expect(colors.size).toBe(4);
  });

  it('keeps the length within the limits of the backend', () => {
    expect(clampLength(5)).toBe(8);
    expect(clampLength(500)).toBe(128);
    expect(clampLength(20.4)).toBe(20);
    expect(clampLength(Number.NaN)).toBe(20);
  });
});
