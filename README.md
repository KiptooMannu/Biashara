<div align="center">

# BIASHARA

**An AI-powered ERP built for micro, small and medium enterprises. Inventory, sales, CRM, procurement, finance, HR and business intelligence in one platform with an assistant that explains the numbers instead of just reporting them.**

Inventory · Sales & POS · CRM · Procurement · Finance · Accounting · HR · Assets · Projects · Business Intelligence · AI Assistant

*From record-keeping to decision-making — every number arrives with a cause and a recommended action.*

</div>

---

## Start here

| I want to… | Go to |
|---|---|
| **Run it** | [Quick start](#quick-start) — two commands |
| **Be walked through it** | **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)** — step-by-step, what to click and why |
| **See the pitch** | **[docs/BIASHARA-Pitch-Deck.html](docs/BIASHARA-Pitch-Deck.html)** — open in a browser, arrow keys to navigate |
| **Judge the engineering** | **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** — decisions and trade-offs |
| **Ship it** | **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)** — Docker image and Render blueprint |
| **Poke the API** | http://localhost:8080/swagger-ui.html — 65 endpoints, live |

---

## Quick start

Two terminals from the project root. **The database is already provisioned and
seeded** — no migration, no local database, no import.

```bash
# Terminal 1 — API on :8080. Wait for "Started BiasharaApplication".
cd backend
mvn spring-boot:run

# Terminal 2 — web app on :5173
cd frontend
npm install        # first run only
npm run dev
```

Open **http://localhost:5173**. The sign-in screen lists every seeded account with a
one-click **Enter** button — you never need to type a credential.

### Prerequisites

JDK 21+ (built and verified on JDK 25), Maven 3.9+, Node 20+. Nothing else.

### Confirming it is healthy

The backend prints this on every boot:

```
======================= BIASHARA seeded data =======================
  Permissions                  63
  Roles                        14
  Users                        13
  …
-------------------------------------------------------------------
  All 27 tables have at least 10 rows.
===================================================================
```

Any table below the floor is flagged `<-- BELOW MINIMUM` right there, so an empty
screen is caught in a log line rather than during a walkthrough.

> **First request may take a few seconds.** Neon scales to zero when idle. Load one
> page before presenting.

### Useful flags

```bash
# Rebuild the demo data from scratch (~3 minutes)
mvn spring-boot:run -Dspring-boot.run.arguments=--biashara.seed.reset=true

# Run fully offline on a local file-backed H2 database instead of Neon
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

---

## Demo accounts

Every role in the hierarchy has an account, so access control can be **exercised**
rather than described. Sign in as a cashier and the application is genuinely four
screens; sign in as the owner and it is the whole platform.

### The three to demo with

| Email | Password | Role | Permissions |
|---|---|---|---|
| `owner@biashara.demo` | `Owner@123` | Business Owner | **61** |
| `manager@biashara.demo` | `Manager@123` | General Manager | **31** |
| `cashier@biashara.demo` | `Cashier@123` | POS Cashier | **7** |

### The rest

| Email | Password | Role | Permissions |
|---|---|---|---|
| `superadmin@biashara.demo` | `Super@123` | Platform Super Admin | 63 |
| `finance@biashara.demo` | `Finance@123` | Finance Manager | 22 |
| `sales@biashara.demo` | `Sales@123` | Sales Manager | 21 |
| `admin@biashara.demo` | `Admin@123` | Business Administrator | 18 |
| `inventory@biashara.demo` | `Stock@123` | Inventory Manager | 18 |
| `hr@biashara.demo` | `HrDemo@123` | HR Manager | 17 |
| `procurement@biashara.demo` | `Procure@123` | Procurement Manager | 15 |
| `accountant@biashara.demo` | `Accounts@123` | Accountant | 10 |
| `store@biashara.demo` | `Store@123` | Storekeeper | 6 |
| `newuser@biashara.demo` | `Temp@123` | HR Officer | 5 |

`newuser` deliberately **forces a password change** on sign-in — it demonstrates the
invitation and first-login flow rather than skipping past it.

---

## The five-minute tour

Full version with talking points in **[docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md)**.

1. **Owner → dashboard.** AI insights lead, each stating what happened, *why*, and
   what to do. Then 14 KPIs with like-for-like comparisons, and a health score broken
   into six weighted components.
2. **`/assistant` → "Why did profits fall?"** It answers from live queries and shows
   the figures and query names it used. It will tell you profit is actually *up* — it
   contradicts the question rather than agreeing with it.
3. **Sign in as the cashier.** The sidebar collapses to four items. Type `/finance`
   directly — the **server** refuses it, not just the UI.
4. **Make a sale in the POS.** Stock decrements, a ledger movement appears with the
   resulting balance and invoice reference, payment recorded, loyalty points updated
   — one transaction.
5. **HR Manager → Users & Roles → Add user.** The role dropdown only offers roles
   below their level. Creating a Business Owner returns 403; creating into Finance
   returns 403.
6. **Owner → Audit Trail.** Every action from the tour, including the blocked
   attempts.

---

## What makes it different

### The assistant shows its working

It is **deliberately not a language model**. It classifies the question, runs the
analytics queries that answer it, and composes a reply from what came back — then
returns the values it used (`dataPoints`) and names the queries (`dataSource`).

An owner about to reorder stock has to be able to check the claim. A rule-based
assistant over real queries **cannot invent a number** because it never generates
one. One method, `AiAssistantService.compose()`, is the swap point for Spring AI: the
query layer becomes the model's tool surface, and the grounding — the hard part —
already exists.

### Authorization is permission-based

63 permissions, checked per endpoint with
`@PreAuthorize("hasAuthority('inventory.product.create')")`. Roles are database rows
bundling permissions, so a business can invent a role at runtime without a redeploy.

Role **hierarchy** is a single integer: you may only create or assign roles at a
strictly higher level number than your own. That is what makes *"managers can only
create users within their own department"* enforceable rather than aspirational —
and it is verified, not asserted.

### The numbers are internally consistent

- Cost of goods is captured **per line at the moment of sale**, so gross profit stays
  correct after supplier prices move.
- `Product.currentStock` is a cached projection of the movement ledger; both are
  written in one transaction, and the seeder back-calculates opening balances so the
  ledger lands **exactly** on recorded stock.
- Customer tiers, RFM scores, churn risk and lifetime value are **computed** from
  purchase history. Monetary score is a percentile *within the tenant* — a "VIP" at a
  kiosk and at a wholesaler are different numbers.
- Supplier reliability comes from observed delivery dates against agreed lead times.

---

## Architecture at a glance

**Modular monolith.** Each of the twelve top-level packages under `com.biashara` is a
bounded context (`iam`, `inventory`, `sales`, `crm`, `procurement`, `finance`, `hr`,
`asset`, `project`, `analytics`, `ai`, `notification`) mapping 1:1 onto a service
boundary in the target microservice design. Extracting a service later means lifting
a package, not untangling a codebase — one deployable to run and reason about today,
with the seams already drawn.

**Multi-tenancy** is shared-schema with a `tenant_id` discriminator. Every
tenant-owned row extends `TenantAwareEntity`, every query is scoped by tenant, and
the tenant comes from the caller's token — never from a request parameter. A request
cannot ask for another business's data because it never gets to name the business.

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.5, Java 21 bytecode on JDK 25, Maven |
| Data | Spring Data JPA / Hibernate 6.6, Neon serverless PostgreSQL 18 |
| Security | Spring Security, JWT + rotating server-side refresh tokens, BCrypt |
| API docs | OpenAPI 3 / Swagger UI |
| Frontend | React 18, TypeScript, Vite 6 |
| UI | shadcn/ui (Radix + CVA + tailwind-merge), Tailwind CSS |
| Charts | ApexCharts |
| State | Zustand, axios with single-flight token refresh |

Full reasoning, including the trade-offs and the known limits, is in
**[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

---

## Four decisions worth explaining

**Sequence-backed ids, not `IDENTITY`.** `IDENTITY` forces Hibernate to round-trip per
insert to read the key back, silently disabling JDBC batching. Seeding was ~12,000
individual round trips to a US-East database and took **over 40 minutes**. A pooled
sequence hands out 50 ids per call: the same seed now runs in **~3 minutes**.

**`prepareThreshold=0` on Neon.** The pooler multiplexes clients onto shared server
connections and caches statement plans against them, so after a schema change a
cached plan raises *"cached plan must not change result type"*.

**`open-in-view: false`.** The session closes with the transaction, so DTO mapping
cannot lazily trigger queries from the view layer. The cost is that every association
a response needs must be declared in an `@EntityGraph` — nine endpoints failed loudly
this way during development. With the Spring Boot default left on, all nine would
have silently worked while issuing N+1 queries per row.

**Like-for-like period comparisons.** Month-to-date revenue is compared against the
previous month *up to the same day*. Comparing 26 days against a full 30-day month
showed a spurious 5.5% decline caused purely by the missing days.

---

## Demo data

One seeded business: **GreenMart Supermarket**, a 10-branch Nairobi chain. The seeder
is idempotent (it keys off the tenant slug) and reports its own row counts on every
boot.

| | | | |
|---|---|---|---|
| Permissions | 63 | Sales | 2,248 |
| Roles | 14 | Inventory movements | 8,051 |
| Users | 13 | Purchase orders | 50 |
| Departments | 10 | Invoices | 40 |
| Branches | 10 | Payments | 87 |
| Warehouses | 10 | Expenses | 40 |
| Categories | 20 | Journal entries | 21 |
| Products | 55 | Attendance records | 1,716 |
| Suppliers | 20 | Leave requests | 16 |
| Customers | 22 | Payroll records | 66 |
| Customer interactions | 72 | Assets | 16 |
| Employees | 22 | Projects / tasks | 12 / 18 |
| AI insights | 14 | Notifications | 17 |

Sales volume follows weekday, weekend and month-end salary patterns, so trend charts
have a shape a shopkeeper would recognise. Monthly revenue ≈ 2.38M against a 2.5M
target, producing 735K gross profit against 502K operating expenses — a **positive**
net position. A dashboard reporting a loss while scoring the business "excellent"
would be incoherent.

---

## Verification

Checked against the running stack, not asserted:

| Check | Result |
|---|---|
| Backend compiles | 163 source files, clean |
| Frontend typechecks | `tsc --noEmit` — 0 errors |
| REST endpoints | **65 / 65** return 200 as Business Owner |
| Seed coverage | **27 / 27** tables at or above the 10-row floor |
| RBAC per role | Owner / GM / Cashier / Finance / HR probed; denials match the matrix |
| Privilege escalation | HR Manager → Business Owner returns **403** |
| Department scope | HR Manager → user in Finance returns **403** |
| POS transaction | Stock 64 → 61 with matching `STOCK_OUT` movement, balance and invoice ref |
| Assistant grounding | Refutes a false premise; returns figures and query names |
| Full path | Browser → Vite proxy → Spring Boot → Neon |

---

## Not built

Stated plainly — a demo that implies more than it does is worse than one that
doesn't. These are from the wider product vision and are out of scope for this build:

- **Messaging and search infrastructure** — Kafka/RabbitMQ, Elasticsearch, Redis.
- **External channels** — no SMTP, SMS or WhatsApp gateway. Invitations are persisted
  and surfaced in the admin UI instead of emailed, which is precisely what keeps the
  first-login flow demonstrable.
- **OAuth2 and social sign-in.** **Two-factor auth** is modelled on the user record
  but has no OTP channel.
- **File storage** (Cloudinary) and **receipt OCR**.
- **Offline-first POS synchronisation** and PWA packaging.
- **Payment gateways** — M-Pesa, Stripe, Flutterwave. Payment *methods* are modelled
  and recorded; no gateway is called.
- **Flyway migrations** — schema is managed by Hibernate `ddl-auto`, which is right
  for a demo and wrong for production.
- **Drag-and-drop report builder** and scheduled report delivery.
- **Microservice deployment** — the boundaries exist as packages, not as services.

Known limits of what *is* built — invoice-number races, `ddl-auto`, analytics on live
tables, token staleness, flat sales-velocity averaging — are listed with reasoning in
[docs/ARCHITECTURE.md § 11](docs/ARCHITECTURE.md).

---

## Project layout

```
backend/
  src/main/java/com/biashara/
    common/           BaseEntity, TenantAwareEntity, exceptions, error handling
    config/           SecurityConfig, OpenApiConfig
    iam/              tenants, users, roles, permissions, audit, sessions
    inventory/  sales/  crm/  procurement/  finance/  hr/  asset/  project/
    analytics/        dashboard aggregation, business health, reports
    ai/               insights, assistant
    notification/     in-app inbox
    seed/             DataSeeder + five staged seeders + SeedReporter
  src/main/resources/
    application.yml            common config
    application-neon.yml       Neon (default profile)
    application-h2.yml         offline fallback profile

frontend/
  src/
    components/ui/    shadcn/ui component source (owned, not a dependency)
    components/       layout, shared, charts, dashboard
    pages/            21 screens
    lib/              api client, permissions, navigation, formatting
    hooks/  store/
docs/
  pitch-deck.html     presentable deck — arrow keys, or print to PDF
  DEMO_SCRIPT.md      step-by-step walkthrough with talking points
  ARCHITECTURE.md     technical deep dive
```

---

## Security note

The Neon credentials are committed in
`backend/src/main/resources/application-neon.yml` so the project runs with no setup.
Every value is overridable by environment variable — `BIASHARA_DB_URL`,
`BIASHARA_DB_USER`, `BIASHARA_DB_PASSWORD`, `BIASHARA_JWT_SECRET`.

**Rotate that database password once the demo is over.**
