# Valkyria Backend 🛡️🎸

[![CI](https://github.com/asa7ur/valkyria-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/asa7ur/valkyria-backend/actions/workflows/ci.yml)

REST API for **Valkyria**, a music festival platform: lineup, ticket and camping sales with Stripe, PDF tickets with a QR code, transactional emails and an admin panel.

🌐 **Live at [valkyriafest.es](https://valkyriafest.es)** · 🖥️ Frontend: [valkyria-frontend](https://github.com/asa7ur/valkyria-frontend)

> **This is a portfolio project.** The festival is fictional and payments run in Stripe test mode, so no real money is ever charged.

## ✨ Features

* **Catalog**: artists, stages, performances and sponsors, with image uploads converted to WebP.
* **Sales**: ticket and camping types with stock control. Stock is reserved atomically, so two people buying the last ticket at the same time cannot oversell it.
* **Payments**: Stripe Checkout, webhook with signature verification, idempotent confirmation, and a scheduled job that cancels unpaid orders and releases their stock.
* **Documents**: tickets rendered to PDF (OpenPDF) with a QR code per ticket, sent by email after payment.
* **Email**: HTML templates (Thymeleaf) with an inline logo, in Spanish or English depending on the order.
* **Authentication**: JWT signed with an RSA key from a keystore, plus Google sign-in (OAuth 2.0) with a single-use exchange code. Changing the password or disabling an account invalidates existing tokens.
* **Roles**: `USER`, `MANAGER` and `ADMIN`, enforced per endpoint with `@PreAuthorize`.
* **Guest checkout**: buying without an account, using an email address.
* **Hardening**: per-IP rate limiting on the auth endpoints, validated and bounded pagination, unified error responses and i18n messages (Spanish/English).
* **API docs**: OpenAPI 3 with Swagger UI, disabled in production.

## 🛠️ Tech Stack

| Area | Tech |
|:--|:--|
| Language | Java 25 |
| Framework | Spring Boot 4 (Web, Data JPA, Security, OAuth2 Client, Mail, Thymeleaf, Validation) |
| Database | MariaDB 12.2, migrations with Flyway |
| Mapping | MapStruct, Lombok |
| Payments | Stripe Java SDK |
| Documents | OpenPDF + openhtmltopdf, ZXing for QR codes |
| Testing | JUnit 5, AssertJ, Testcontainers (real MariaDB) |
| Build & deploy | Maven, Docker, GitHub Actions, GHCR |

## 📁 Project Structure

```text
src/main/java/.../valkyria/
├── controllers/    # REST endpoints
├── services/       # Business logic
├── repositories/   # Spring Data JPA
├── entities/       # JPA model
├── dtos/           # Request and response payloads
├── mappers/        # MapStruct entity <-> DTO
├── config/         # Security, CORS, OpenAPI, rate limiting
├── events/         # Domain events (async post-payment documentation)
├── exceptions/     # AppException and the global handler
├── validation/     # Custom constraints (password policy, field match)
└── utils/          # JWT and helpers

src/main/resources/
├── db/migration/   # Flyway schema (runs everywhere)
├── db/seed/        # Demo data (dev profile only)
├── templates/      # Email and PDF templates
└── messages*.properties
```

## 🚀 Getting Started

### Prerequisites

* **JDK 25**
* **Docker** — for the local database and for the tests (Testcontainers)

Maven is not required: the project ships the Maven wrapper (`./mvnw`).

### 1. Configuration

Create a `.env` file in the project root. The application reads it at startup (dotenv-java), and `docker-compose.yml` uses it too.

```dotenv
# Database
DB_URL=jdbc:mariadb://localhost:3306/valkyria
DB_USER=valkyria
DB_PASSWORD=change-me
DB_DRIVER=org.mariadb.jdbc.Driver
DB_DATABASE=valkyria
DB_ROOT_PASSWORD=change-me

# URLs
APP_URL=http://localhost:4200
APP_BACKEND_URL=http://localhost:8080
CORS_ALLOWED_ORIGINS=http://localhost:4200

# JWT signing key (see step 2)
JWT_KEYSTORE_PATH=certs/jwt-keystore.jks
JWT_KEYSTORE_PASSWORD=change-me

# Google sign-in
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...

# SMTP
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...

# Stripe (test keys)
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Optional: `JWT_KEYSTORE_ALIAS` (defaults to `jwt-keypair`), `MAIL_FROM` and `MAIL_FROM_NAME` (defaults to the SMTP user and `Valkyria Festival`).

### 2. JWT keystore

Tokens are signed with an RSA key pair kept in a JKS keystore:

```bash
mkdir -p certs
keytool -genkeypair -alias jwt-keypair -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=Valkyria" -storetype JKS -keystore certs/jwt-keystore.jks \
  -storepass "$JWT_KEYSTORE_PASSWORD" -keypass "$JWT_KEYSTORE_PASSWORD"
```

### 3. Database

```bash
docker compose up -d db
```

### 4. Run

```bash
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`. This command activates the **dev** profile, which also loads the demo catalog from `db/seed`. The `prod` profile (`SPRING_PROFILES_ACTIVE=prod`) skips the seed data and turns the API docs off.

Swagger UI: <http://localhost:8080/swagger-ui-custom.html> · OpenAPI JSON: <http://localhost:8080/api-docs>

### Stripe webhooks in development

Stripe cannot reach `localhost`, so forward the events with the Stripe CLI and use the signing secret it prints:

```bash
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe
```

## 🧪 Testing

```bash
./mvnw verify
```

Docker must be running: the integration tests start a real MariaDB container with Testcontainers and migrate it with Flyway, so they need no local database and no real credentials. Stripe, Google and SMTP are never called.

## 📦 Deployment

Every push and pull request to `develop` or `main` runs the test suite on GitHub Actions. Pushing a `vX.Y.Z` tag builds a Docker image, publishes it to **GHCR** as `ghcr.io/asa7ur/valkyria-backend:X.Y.Z` and, after a manual approval, deploys it to the production VPS over SSH.

The server runs Docker Compose behind nginx (HTTPS with Let's Encrypt). The compose file and the deployment scripts live in [`deploy/`](deploy/); the database is backed up automatically before every deployment.

---

Made by [Garik Asatryan](https://github.com/asa7ur).
