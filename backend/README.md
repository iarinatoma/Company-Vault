# Spring Boot Starter Template

The starting point for new services. The ArchUnit suite (`shared/architecture/ArchitectureTest`)
enforces the layering and naming rules of the code base.

Stack: Spring Boot 4.1, Java 25, Spring MVC (virtual threads on), Spring Data JPA +
PostgreSQL 18, Spring Security 7 (stateless JWT + 2FA + rotating refresh cookies), Liquibase,
Lombok, MapStruct, springdoc.

## Getting started

All development defaults are prefilled in `src/main/resources/application.properties`
(database, JWT secret, encryption key), so no `.env` file is needed.

1. Start a local PostgreSQL 18 with an empty database named `template`
   (user `postgres`, password `root`, port 5432 — as configured in `application.properties`).
2. Run the application:

```bash
./mvnw spring-boot:run      # http://localhost:8080
# or, without a local PostgreSQL (throwaway Testcontainers database, needs Docker):
./mvnw spring-boot:test-run
```

Liquibase creates the schema and the development admin on first start.

### Development login

| E-mail              | Password          | Role  |
| ------------------- | ----------------- | ----- |
| `admin@example.com` | `Admin-Starter-1` | ADMIN |

The account is seeded by `db/changelog/changes/2026-08-12-10-20-seed-dev-admin.xml` in the
Liquibase context `dev`. The `prod` profile uses the context `prod`, so it is **never** created
in production. Change or remove it before deploying anywhere real.

Swagger UI (dev/test only): http://localhost:8080/swagger-ui.html


### Master key (encryption)

The master key encrypts all data keys and is never stored in the database or the repository.
It is read, in this order, from:

1. the environment variable `ENCRYPTION_KEY` (or the git-ignored `.env` file locally), or
2. a mounted secret file named `ENCRYPTION_KEY` in `SECRETS_DIR` (default `/run/secrets/`,
   where Docker secrets are mounted).

The value must be base64 of exactly 32 random bytes (AES-256); otherwise the application refuses
to start. Generate one with: `openssl rand -base64 32`.

#### Master-key backup and recovery

The administrator dashboard provides a **Download master key backup** action. It downloads the
currently configured key as a Base64 text file accepted directly as `ENCRYPTION_KEY`. The
download endpoint is restricted to the `ADMINISTRATOR` role and sends no-cache headers; the
application never stores another copy in the database.

Treat this file as a credential with access to **every vault**: keep it in a separate, encrypted,
access-controlled offline backup location, restrict who can retrieve it, and never put it in Git,
email, logs, or beside the database backup. Create and verify a protected copy when provisioning a
key and again after any planned key rotation.

To recover after losing the deployed copy, restore the **exact same** key from the protected
backup as `ENCRYPTION_KEY` (or as the mounted secret file) and restore the corresponding database
backup, then restart the application. Without that exact key, the wrapped DEKs in `data_keys`
cannot be decrypted and **all secrets are permanently unreadable**. The admin download can only
back up the key currently loaded by a running application; it cannot recover an already-lost key.

Each vault has a separate DEK. Deleting a DEK from the live database makes only that vault's
secrets unreadable; the master-key backup alone cannot restore it. A separate database backup
containing that DEK may restore it, so protect database backups as sensitive encrypted data too.
Do not delete `data_keys` rows as a recovery or rotation procedure. A master-key rotation must
re-wrap every DEK with the new key before replacing the configured key; simply changing
`ENCRYPTION_KEY` makes all vaults unreadable.

### Account registration and email

Public registration accepts only an e-mail address (`POST /api/v1/auth/register` with
`{"email":"you@example.com"}`). It creates a standard member account and sends a one-time
password setup link; it also creates a personal vault with its own encryption key and grants the
new user manager access. The user chooses their password through the same page used for password
resets. The link expires after the configured password-reset token TTL and can be used once.

For local development, email sending is enabled and directed to Mailpit instead of real
recipients. Start the local email catcher from the `backend` directory:

```bash
docker compose up -d mailpit
```

Mailpit's inbox is available at http://localhost:8025. The backend submits mail through
`localhost:1025`; signup messages appear in Mailpit, where you can open the one-time link and set
the new account's password. No domain, provider account, or SMTP credentials are needed for
local testing. Mailpit is configured to start automatically when Docker starts and stop when
Docker stops.

To disable local email capture, set `EMAIL_ENABLED=false`. To send real email in a deployed
environment, use an SMTP provider with a verified sender domain, and configure
`EMAIL_ENABLED=true`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`,
`SMTP_STARTTLS`, `EMAIL_FROM`, and `PASSWORD_SETUP_LINK_BASE_URL` in the deployment environment.
Never commit provider credentials. The frontend submits only the email address and provides
the password setup page at the configured link URL.

### Secret types

Every secret has a `type`: `LOGIN`, `API_KEY` (the default), `NOTE` or `CUSTOM`. `value` is always
the main value (the password of a login, the key or token, the text of a note) and is encrypted
with the vault's data key. A `LOGIN` can also have `fields.username` and `fields.url`, and every
type can have up to 20 custom key-value fields in `fields.customFields`. These fields are stored as
JSON in `secrets.encrypted_fields`, encrypted with the same data key. Only the type is plain
metadata, so the secret list shows it without decrypting anything. Only the explicit reveal,
`GET /api/v1/vaults/{vaultId}/secrets/{secretId}/value`, returns `value` and `fields`. On an edit,
a missing `type` or `fields` keeps the current one. Secrets created before secret types existed
are `API_KEY` without fields.

### Key rotation

**Master key.** Only the wrapped DEKs and the TOTP secrets are encrypted directly with the master
key, so rotating it never touches the secrets themselves:

1. Generate a new key: `openssl rand -base64 32`.
2. Set `ENCRYPTION_KEY` to the new key and `ENCRYPTION_KEY_PREVIOUS` to the old one, then
   restart. New data uses the new key; data still under the old key stays readable.
3. As an administrator call `POST /api/v1/keys/rotate-master` (Swagger UI). It re-encrypts every
   DEK and TOTP secret with the new key in one transaction and returns how many it moved.
4. Remove `ENCRYPTION_KEY_PREVIOUS` and restart. Back up the new key; the old one can be destroyed.

If step 3 fails because a value opens with neither key, nothing is changed: keep both keys set
and investigate before removing the old one.

**Data key (DEK) of one vault.** `EnvelopeEncryption.rotateKey(vault, ciphertexts)` creates a new
DEK, re-encrypts the vault's secrets from the old DEK to the new one and stores the new DEK
wrapped with the master key. The caller saves the returned ciphertexts in the same transaction, so
a failure keeps the old DEK. Rotate a vault's DEK when someone with access to it leaves or a
compromise is suspected. There is no endpoint yet: the secrets feature calls it, because only it
knows the vault's ciphertexts.

### Password generator

`GET /api/v1/password-generator?length=20&lowercase=true&uppercase=true&digits=true&symbols=true`
(any signed-in user; all parameters optional, defaults shown) returns a random password from
`SecureRandom`, its entropy in bits (length × log2 of the character pool) and a strength label:
`WEAK` (< 40 bits), `FAIR` (< 60), `STRONG` (< 80), `VERY_STRONG`. The length must be 8–128 and
at least one character set must be on; every chosen set appears at least once. The password is
only in the response body: it is never stored or logged.

## Commands

```bash
./mvnw verify                       # full build: unit tests + integration tests (needs Docker)
./mvnw test                         # unit tests ONLY — *IT classes do NOT run here
./mvnw verify -Dit.test=ClassNameIT # single integration test class
./mvnw spring-boot:build-image      # container image via Buildpacks (no Dockerfile on purpose)
```

## Layout

```
auth/  user/          feature slices: api/ dto/ model/ event/ repository/ service/ mapper/
shared/               config/ crypto/ error/ security/ model/ — only what spans features
infra/                notification/ — ports + adapters for the outside world
db/changelog/changes/ Liquibase, one table per file, datetime-named
```

## Secrets

The values in `application.properties` are for local development only. For other environments
override them with environment variables or a git-ignored `.env` / `.env-prod` file
(`spring.config.import`). A committed real secret is an incident, not a cleanup.



Circa Ioan Andrei