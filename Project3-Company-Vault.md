# Project 3: Company Vault (Secrets and Password Manager)

**Course:** Proiect colectiv
**Team size (suggested):** 5–7 students
**Duration (suggested):** 1 semester (~14 weeks)
**Technology (given):** Spring Boot, Angular, PostgreSQL. Teams receive a starter project in which registration and login are already implemented.

---

## 1. Context and Motivation

Teams routinely share credentials such as database passwords, API keys, service accounts, Wi-Fi codes, and licence keys. Too often these end up in chat messages, e-mails, spreadsheets, or source code repositories, where they are insecure, impossible to audit, and hard to rotate when someone leaves.

This project aims to build a secure, company-internal vault, similar in spirit to Bitwarden, 1Password Teams, or HashiCorp Vault. It stores secrets in encrypted form, organises them per project or as general company secrets, controls precisely who can see or change them, and records every access in an audit trail.

> **Security note for students:** this project is an exercise in applied security. Do not invent your own cryptography. Use well-established, vetted libraries and algorithms, and document your security decisions.

## 2. Project Goals

- Store secrets (passwords, API keys, notes, certificates, files) securely, always encrypted at rest.
- Organise secrets into projects and general (company-wide) collections.
- Provide fine-grained, role-based access control for users and groups.
- Record a complete audit trail of who accessed or changed what, and when.
- Support secret lifecycle features: versioning, expiry reminders, and revocation.
- Deliver a maintainable, tested, documented system built with professional team practices (Git, code review, CI, agile process).

## 3. User Roles

| Role | Description |
|------|-------------|
| **Member (User)** | Accesses secrets in vaults/projects they were granted, and can manage a personal vault. |
| **Project Owner / Manager** | Manages a project's vault: creates secrets and folders, grants and revokes access for members. |
| **Auditor** | Read-only access to audit logs and reports, with no access to secret values. |
| **Administrator** | Manages users, groups, global policies and general vaults, and can recover access. |

Access levels on a vault, project, or secret: **No access, Read (view/copy), Write (edit), Manage (share/delete)**.

## 4. Functional Requirements

### 4.1 Roles and Account Protection
- Role-based access control (Member / Project Owner / Auditor / Administrator).
- Brute-force protection (rate limiting, temporary lockout).

### 4.2 Vaults, Projects, and Folders
- **Personal vault:** private to each user.
- **General vaults:** company-wide collections (e.g. "IT Infrastructure", "Shared Accounts", "Wi-Fi & Office").
- **Project vaults:** one per project, with its own members and access levels.
- Folders/tags inside a vault for organisation; move secrets between folders.
- Create, rename, archive, and delete vaults and projects (with confirmation).

### 4.3 Secret Management
- Secret types (with type-specific fields):
  - Login (username, password, URL)
  - API key / token
  - Secure note
  - Custom key-value fields
- Create, view, edit, and delete secrets.
- Secret values **masked by default**, with "reveal" and "copy to clipboard" (clipboard auto-clear after a short time).
- **Built-in password generator** with configurable length and character sets, and a strength indicator.
- **Version history:** every change stores a previous version, and a user can view and restore older versions (with permission).
- Metadata: description, tags, owner, creation and modification dates, optional expiry date.
- Search and filter by name, tag, and project. Searching must not require decrypting every secret (index only non-sensitive metadata).

### 4.4 Sharing and Access Control
- Grant access to a vault, project, or folder for a **user or group**.
- Define groups (e.g. "Backend Team", "DevOps") managed by the admin.
- Per-resource permission levels (read / write / manage) with inheritance (project, folder, secret) and the ability to override.
- Revoke access immediately; list "who has access to this secret".
- Optional: time-limited access (e.g. a contractor for 7 days).
- **One-time share link** for a secret, with a time limit and a view limit.
- Optional: **access requests**, where a user asks for access and the owner approves or denies it.

### 4.5 Audit Log and Monitoring
- Immutable log of events: login, failed login, secret viewed/copied, created, edited, deleted, shared, permission changed, export, admin actions.
- Each entry records who, what, when, from where (IP/user agent), and the target.
- Audit view with filters (user, project, action, date range) and CSV export.

### 4.6 Secret Lifecycle
- Expiry dates with reminder notifications (e-mail/in-app) to the owner.
- Soft delete with a trash bin and retention period; permanent deletion afterwards.

### 4.7 Administration
- User management: invite, deactivate, change roles.
- Group management.
- Overview of all vaults and projects (metadata only; admins should not automatically see secret values).

### 4.8 Cryptography and Key Management (core requirement)
Teams must design and document a key management scheme:
- **Encryption at rest:** each secret is encrypted with AES-256-GCM (or ChaCha20-Poly1305) using authenticated encryption.
- **Envelope encryption:** each vault/project has a data encryption key (DEK), and the DEKs are encrypted with a master key.
- **Key storage:** the master key is never stored in the database or repository (use environment secrets or a file mount).
- **Transport security:** HTTPS/TLS only.
- **Server-side encryption model:** the server holds the keys, and the admin can recover access. Document key rotation and recovery.

## 5. Non-Functional Requirements

- **Security first:** OWASP Top 10 awareness, parameterised queries, output encoding, CSRF/XSS protection, secure HTTP headers.
- **No secrets in logs, error messages, URLs, or analytics.**
- **Usability:** security should not make the app painful; clear UI, fast copy-and-fill workflows, responsive design.
- **Maintainability:** layered architecture, clean code, documented API (OpenAPI/Swagger).
- **Testing:** unit tests (including crypto round trips), integration tests (authorisation), security tests (access to other users' secrets must fail), and a few end-to-end tests.
- **Deployment:** reproducible setup via Docker / docker-compose with HTTPS.
