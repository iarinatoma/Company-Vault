import { signal, WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthStore } from './auth-store';
import { IDLE_TIMEOUT_MS, IdleLogout } from './idle-logout';

const TIMEOUT = 1000;

describe('IdleLogout', () => {
  let loggedIn: WritableSignal<boolean>;
  let logout: ReturnType<typeof vi.fn>;
  let idle: IdleLogout;

  beforeEach(() => {
    vi.useFakeTimers();
    loggedIn = signal(false);
    logout = vi.fn(async () => loggedIn.set(false));
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: { isLoggedIn: loggedIn.asReadonly(), logout } },
        { provide: IDLE_TIMEOUT_MS, useValue: TIMEOUT },
      ],
    });
    idle = TestBed.inject(IdleLogout);
    TestBed.tick();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function signIn(): void {
    loggedIn.set(true);
    TestBed.tick();
  }

  it('does nothing while nobody is signed in', () => {
    vi.advanceTimersByTime(TIMEOUT * 5);

    expect(logout).not.toHaveBeenCalled();
    expect(idle.signedOutForInactivity()).toBe(false);
  });

  it('signs the user out after the timeout without activity', () => {
    signIn();

    vi.advanceTimersByTime(TIMEOUT - 1);
    expect(logout).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(logout).toHaveBeenCalledTimes(1);
    expect(idle.signedOutForInactivity()).toBe(true);
  });

  it('restarts the countdown on user activity', () => {
    signIn();

    vi.advanceTimersByTime(TIMEOUT - 100);
    document.dispatchEvent(new KeyboardEvent('keydown'));
    vi.advanceTimersByTime(TIMEOUT - 100);

    expect(logout).not.toHaveBeenCalled();

    vi.advanceTimersByTime(100);
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('stops counting after a manual sign-out', () => {
    signIn();
    loggedIn.set(false);
    TestBed.tick();

    vi.advanceTimersByTime(TIMEOUT * 5);

    expect(logout).not.toHaveBeenCalled();
    expect(idle.signedOutForInactivity()).toBe(false);
  });

  it('clears the inactivity notice when the user signs in again', () => {
    signIn();
    vi.advanceTimersByTime(TIMEOUT);
    TestBed.tick();
    expect(idle.signedOutForInactivity()).toBe(true);

    signIn();

    expect(idle.signedOutForInactivity()).toBe(false);
  });
});