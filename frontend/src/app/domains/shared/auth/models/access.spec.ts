import { hasAccess } from './access';

describe('hasAccess', () => {
  it('allows the same level and every lower one', () => {
    expect(hasAccess('MANAGE', 'MANAGE')).toBe(true);
    expect(hasAccess('MANAGE', 'WRITE')).toBe(true);
    expect(hasAccess('WRITE', 'READ')).toBe(true);
    expect(hasAccess('READ', 'READ')).toBe(true);
  });

  it('denies a higher level', () => {
    expect(hasAccess('READ', 'WRITE')).toBe(false);
    expect(hasAccess('WRITE', 'MANAGE')).toBe(false);
  });

  it('denies everything for NO_ACCESS or an unknown level', () => {
    expect(hasAccess('NO_ACCESS', 'READ')).toBe(false);
    expect(hasAccess(null, 'READ')).toBe(false);
    expect(hasAccess(undefined, 'READ')).toBe(false);
  });
});