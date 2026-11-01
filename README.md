# TNB Digital Banking Platform (DBP)

Application code for the Thamizh Digital Bank Digital Banking Platform MVP.
Technology decisions: [ADR-001](docs/adr/ADR-001-technology-stack.md).

> Fictional bank, practice project. All data is synthetic.

## Current scope (Sprint 1, in progress)

| Jira | Feature | Status |
|---|---|---|
| TNB-4 | Login with Customer ID + password + OTP | API ready |
| TNB-3 | Lock login after 3 consecutive failed attempts | API ready (unlock flow in a later PR) |
| TNB-5 | Reset forgotten password | Not started |
| TNB-6 | Automatic logout after inactivity | Not started |

## Run it locally

Prerequisite: Docker Desktop running.

```bash
docker compose up --build
```

The first build downloads dependencies and takes a few minutes. When you see
`Started DbpApplication`, the app is ready.

| What | URL |
|---|---|
| Swagger UI (try the APIs) | http://localhost:8080/swagger-ui.html |
| OpenAPI spec (JSON) | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |

Stop with `Ctrl+C`, then `docker compose down`.
To wipe the database and start fresh: `docker compose down -v`.

## Database (for DBeaver)

| Setting | Value |
|---|---|
| Host / Port | localhost / 5432 |
| Database | tnbdbp |
| User / Password | tnb / tnb_local_pw (local only) |

Tables: `customers`, `otp_challenges`, `sms_outbox`, `user_sessions`, `flyway_schema_history`.

## Synthetic test customers (dev and sit profiles only)

Password for all: `Test@1234`

| Customer ID | Name | Mobile | State |
|---|---|---|---|
| TNB100001 | Arun Kumar | 9000000001 | Active |
| TNB100002 | Priya Raman | 9000000002 | Active |
| TNB100003 | Karthik S | 9000000003 | Locked (3 failed attempts) |

## Login flow (API)

1. `POST /api/v1/auth/login` with `{"customerId":"TNB100001","password":"Test@1234"}`
   returns a `challengeId` and sends an OTP by simulated SMS.
2. Read the OTP: `GET /api/v1/test-support/sms?mobile=9000000001`
   (or query the `sms_outbox` table).
3. `POST /api/v1/auth/verify-otp` with `{"challengeId":"<from step 1>","otp":"<6 digits>"}`
   returns an `accessToken`.
4. `GET /api/v1/customers/me` with header `Authorization: Bearer <accessToken>`.

Other endpoint: `POST /api/v1/auth/resend-otp` with `{"challengeId":"..."}`.

### Error format

Every error returns the same shape:

```json
{
  "timestamp": "2027-01-18T10:15:30Z",
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid Customer ID or password.",
  "path": "/api/v1/auth/login"
}
```

| Code | HTTP | When |
|---|---|---|
| VALIDATION_ERROR | 400 | Missing field, OTP not 6 digits |
| MALFORMED_REQUEST | 400 | Body missing or not valid JSON |
| OTP_EXPIRED | 400 | OTP older than 5 minutes |
| OTP_ALREADY_USED | 400 | OTP already used to log in |
| INVALID_CREDENTIALS | 401 | Unknown Customer ID or wrong password |
| INVALID_OTP | 401 | Wrong OTP |
| UNAUTHORIZED / SESSION_EXPIRED | 401 | Missing, invalid or expired token |
| CHALLENGE_NOT_FOUND | 404 | Unknown challengeId |
| ACCOUNT_LOCKED | 423 | Login locked after 3 failed attempts |
| RESEND_LIMIT_REACHED | 429 | OTP resent too many times |

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `tnb.auth.otp-validity-seconds` | 300 | OTP lifetime |
| `tnb.auth.max-failed-attempts` | 3 | Failures before lock |
| `tnb.auth.max-otp-resends` | 3 | OTP resends allowed per login |
| `tnb.auth.session-validity-seconds` | 300 | Access token lifetime |

Database connection: `DB_URL`, `DB_USER`, `DB_PASSWORD` environment variables.
Profile `dev` or `sit` loads synthetic test data and enables `/api/v1/test-support`.
These must never be active in UAT or production.

## Run unit tests

```bash
cd backend
mvn test
```

## Project structure

```
tnb-dbp-app/
├── docker-compose.yml          Local stack: PostgreSQL + backend
├── docs/adr/                   Architecture decision records
└── backend/                    Spring Boot (Java 21)
    ├── pom.xml
    ├── Dockerfile
    └── src/
        ├── main/java/com/tnb/dbp/
        │   ├── auth/           Login, OTP, lock (TNB-4, TNB-3)
        │   ├── customer/       Customer entity, /customers/me
        │   ├── otp/            OTP challenges
        │   ├── session/        Access tokens
        │   ├── sms/            Simulated SMS gateway
        │   ├── testsupport/    Test hooks (dev/sit only)
        │   ├── common/         Error handling
        │   └── config/         Beans, OpenAPI info
        └── main/resources/
            ├── application.yml
            └── db/
                ├── migration/  Schema (all environments)
                └── testdata/   Synthetic data (dev/sit only)
```
