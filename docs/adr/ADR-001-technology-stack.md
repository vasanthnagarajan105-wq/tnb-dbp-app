# ADR-001: Technology Stack for the Digital Banking Platform MVP

| Field | Value |
|---|---|
| Project | TNB Digital Banking Platform (DBP) |
| Status | **Accepted** |
| Date | 04-Jan-2027 (Sprint 0) |
| Jira | TNB-7 |
| Deciders | Solution Architect, Tech Lead, QA Lead (Vasanth N) |
| Classification | Internal |
| Location | `docs/adr/ADR-001-technology-stack.md` in the project repository |

## 1. Context

The Bank's RFP (TNB/IT/RFP/2026/001) and SOW require:

- A customer internet banking portal and a staff operations portal sharing one backend (RFP 4.1).
- A mock core banking service behind an integration layer, replaceable later by a licensed CBS (RFP 8).
- Fund transfer in 3 seconds or less for 95% of requests; 500 concurrent users; 99.5% availability (NFR-PERF-01, NFR-CAP-01, NFR-AVL-01).
- Strong security: OWASP Top 10, TLS 1.2+, AES-256 at rest, RBAC, maker-checker (NFR-SEC-01 to 03).
- At least 70% of the regression suite automated at UI, API and database level, run from CI on every build to SIT (RFP 7).
- Go-live on 30-Apr-2027 with a fixed price, so the team must use tools it can deliver with quickly.

## 2. Decision

### 2.1 Application

| Layer | Choice | Notes |
|---|---|---|
| Backend | Java 21 (LTS) + Spring Boot | REST APIs, Spring Security for authentication and RBAC |
| Frontend | React | One codebase for customer portal and staff portal, separated by role |
| Database | PostgreSQL | One schema per service area; Flyway for versioned migrations |
| API contract | OpenAPI 3 (Swagger) | Spec written before code; source for API tests |
| Mock CBS | Spring Boot service behind an integration layer | Portals never call the CBS directly |
| Local runtime | Docker + Docker Compose | One command starts app, mock CBS and database |

Exact library versions are pinned in the build files when the repository is created (TNB-8).

### 2.2 Testing and automation

| Layer | Choice | Role in the project |
|---|---|---|
| UI automation (official) | Selenium WebDriver + Java + TestNG | Regression suite; runs in CI and gates promotion to SIT |
| UI automation (POC) | Playwright + TypeScript | Time-boxed evaluation on Login and Fund Transfer; outcome recorded in a later ADR |
| API automation | RestAssured + Java + TestNG | Contract and functional API tests driven by the OpenAPI spec |
| Manual API exploration | Postman | Exploratory testing, sharing requests with developers |
| Database testing | JDBC + SQL inside the framework; DBeaver for manual queries | Verifies state changes behind UI and API actions |
| Reporting | Allure | One report for UI, API and DB results |
| Performance (SIT phase) | Apache JMeter | Proves NFR-PERF and NFR-CAP |
| Security (SIT phase) | OWASP ZAP | Baseline scan before external VAPT |

### 2.3 Engineering tools

| Area | Choice | Role |
|---|---|---|
| Source control | GitHub | Repositories, pull requests, code review |
| CI/CD (primary) | GitHub Actions | Build, unit tests, API and UI regression on every pull request |
| CI/CD (secondary) | Jenkins | Nightly full regression job; mirrors the Bank's on-premise tooling |
| Work tracking | Jira | Epics, stories, tasks, bugs, sprints |
| Documentation | Jira/Confluence Docs + Markdown in the repo | Working agreement, test strategy, ADRs |

## 3. Architecture overview

```
 Customer browser        Staff browser
        |                      |
        +------ React UI ------+
                   |
             HTTPS / REST (OpenAPI)
                   |
        Spring Boot backend (auth, accounts, transfers, KYC, admin, audit)
             |                         |
        PostgreSQL              Integration layer
                                        |
                              Mock CBS (Spring Boot)
                              (replaced by licensed CBS later)
```

## 4. Rationale

- **Spring Boot:** standard for Indian banks and fintechs; mature security support; the QA team already works in Java, so testers can read the code under test.
- **React:** widely used, large talent pool, component model suits two portals sharing UI parts.
- **PostgreSQL:** free, strict transactional integrity for money movement, strong SQL support for data validation.
- **Selenium + Java as the official UI framework:** existing team skill, lowest delivery risk under a fixed price and fixed date.
- **Playwright POC:** evaluates a faster, less flaky modern tool without putting the go-live at risk.
- **GitHub Actions plus Jenkins:** fast feedback on every change, plus a nightly job matching what most bank IT teams run.

## 5. Alternatives considered

| Area | Alternative | Why not chosen |
|---|---|---|
| Backend | Node.js + Express | Weaker fit with team skills and Java-based test stack |
| Backend | Python + FastAPI | Smaller pool of banking-domain developers |
| Frontend | Angular | Viable; React chosen for team familiarity and hiring market |
| Database | Oracle | Licence cost and heavy local setup for an MVP |
| UI automation | Playwright as the only framework | New language and tool at once raises delivery risk; evaluated as a POC instead |
| CI | Jenkins only | Slower setup; GitHub Actions gives faster pull-request feedback |

## 6. Consequences

**Positive**
- One language (Java) across backend, UI automation and API automation.
- API tests can start as soon as the OpenAPI spec exists, before the UI is built (shift-left).
- Mock CBS behind an integration layer keeps a later CBS swap away from the portals.

**Negative / risks**
- Selenium suites are slower and more prone to flakiness than Playwright; mitigated by explicit waits, stable locators and keeping most checks at API level.
- Running two CI tools adds setup effort; Jenkins limited to one nightly job.
- The Playwright POC competes for QA time; time-boxed to Sprints 2 and 3.

## 7. Delivery note

Application code is written by the development team. The QA Lead owns test strategy, test design, automation frameworks, CI test stages and defect management.

## 8. Revision history

| Version | Date | Author | Change |
|---|---|---|---|
| 1.0 | 04-Jan-2027 | Solution Architect, QA Lead | Initial decision accepted |
