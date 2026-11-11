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

### Account registration and email

Public registration accepts only an e-mail address (`POST /api/v1/auth/register` with
`{"email":"you@example.com"}`). It creates a standard member account and sends a one-time
password setup link; the user chooses their password through the same page used for password
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