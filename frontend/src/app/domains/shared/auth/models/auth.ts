export type Role = 'ADMIN' | 'MANAGER' | 'USER';

export interface User {
  readonly id: string;
  readonly email: string;
  readonly role: Role;
  readonly twoFactorEnabled: boolean;
  readonly createdAt: string;
}

export interface LoginCredentials {
  readonly email: string;
  readonly password: string;
}

/** The refresh token never appears here: the backend sets it as an HttpOnly cookie. */
export interface TokenPair {
  readonly accessToken: string;
  readonly tokenType: string;
  readonly expiresInSeconds: number;
}

export interface TwoFactorChallenge {
  readonly challengeToken: string;
  readonly expiresInSeconds: number;
}

export type LoginResponse = TokenPair | TwoFactorChallenge;

export function isTokenPair(response: LoginResponse): response is TokenPair {
  return 'accessToken' in response;
}

export type LoginOutcome = 'success' | 'two-factor-required';

export const ROLES: readonly Role[] = ['ADMIN', 'MANAGER', 'USER'];
