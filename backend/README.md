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
