# Frontend Starter

Angular 21 starter with a working login flow against the Spring Boot backend, styled with
Tailwind CSS.

## Stack

- Angular 21 (standalone, signals, OnPush) and Signal Forms (`@angular/forms/signals`)
- Tailwind CSS 4 (`src/styles.css`, PostCSS via `.postcssrc.json`)
- Vitest browser mode (Playwright/Chromium)
- `@angular/localize` (EN + DE, see `src/locale/messages.de.json`)

## Getting started

```bash
npm install
npx playwright install chromium   # once, for the tests
npm start                         # http://localhost:4200
```

The dev server proxies `/api` to the backend on `http://localhost:8080` (`proxy.conf.json`),
so the browser only talks to one origin and the HttpOnly refresh cookie works without CORS
setup. Start the backend first (see `../backend/README.md`).

Development login (seeded by the backend in the `dev` Liquibase context):

| E-mail              | Password          |
| ------------------- | ----------------- |
| `admin@example.com` | `Admin-Starter-1` |

## Login flow

1. `POST /api/v1/auth/login` returns an access token in the body; the refresh token is set
   as an HttpOnly cookie.
2. `AuthStore` keeps the access token **in memory only** and loads the profile from
   `GET /api/v1/users/me`.
3. `authInterceptor` adds `Authorization: Bearer ...` to API calls. On a `401` it refreshes
   once (`POST /api/v1/auth/refresh`, single-flight) and retries; if that fails the user is
   logged out and sent to `/login`.
4. On startup `provideAppInitializer` calls the refresh endpoint, so a page reload keeps you
   signed in.
5. `authGuard` protects routes (redirects to `/login?returnUrl=...`), `guestGuard` keeps
   signed-in users off the login page.
6. `POST /api/v1/auth/logout` clears the cookie.

Not implemented yet: the two-factor (TOTP) step. If a user has 2FA enabled, the login page
shows a message instead.

## Structure

```
src/app
├── app.config.ts / app.routes.ts
├── shell/                    header + router outlet
└── domains
    ├── account/              feature-login
    ├── home/                 dashboard with the user CRUD example
    │   ├── data/             UsersClient (HTTP) · UsersStore (signal store) · specs
    │   ├── ui/               user-form · confirm-dialog (native <dialog>)
    │   └── feature-home/     the table, wired to the store
    └── shared/
        ├── auth/             models · data (AuthClient, AuthStore, guard, interceptor)
        └── config/           util (APP_CONFIG)
```

Convention (not enforced by tooling): a domain may only use itself and `shared`; layers only
go downward `feature -> ui -> data -> util -> models`.

## Example: user CRUD with a signal store

`home/data/users-store.ts` is the reference pattern for state in this starter:

- private writable `signal`s hold the state, the public API is read-only signals
  (`items`, `page`, `loading`, `saving`, `loadError`, `saveError`) and `computed` helpers;
- async methods (`load`, `create`, `update`, `remove`) call the `UsersClient`, map backend
  error codes to messages and reload the current page, so the server stays the source of truth;
- components only read signals and call methods (`feature-home`), dialogs are dumb inputs/outputs.

Backend endpoints used: `GET/POST /api/v1/users`, `PUT/DELETE /api/v1/users/{id}`. Listing is
open to ADMIN and MANAGER, writes are ADMIN-only; an admin cannot change their own role or
delete their own account.

## Scripts

```bash
npm start            # ng serve
npm run build        # production build
npm test             # watch mode (Chromium)
npm run test:ci      # headless, single run
npm run lint         # ESLint
```
