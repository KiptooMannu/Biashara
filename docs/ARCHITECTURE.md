# Architecture

The technical deep dive: how BIASHARA is put together, and **why** each choice was
made rather than the alternative. Written for a reviewer who wants to know whether
the engineering holds up, not just whether the screens render.

---

## 1. Shape of the system

```
┌──────────────────────────────────────────────────────────────────┐
│  Browser — React 18 · TypeScript · Vite 6 · shadcn/ui            │
│  21 screens · permission-filtered navigation · ApexCharts        │
└───────────────────────────┬──────────────────────────────────────┘
                            │  JSON over HTTP, Bearer JWT
                            │  (Vite proxies /api in development)
┌───────────────────────────▼──────────────────────────────────────┐
│  Spring Security filter chain                                    │
│  JwtAuthenticationFilter → permissions become authorities        │
│  Stateless · per-endpoint @PreAuthorize · CORS · error mapping   │
└───────────────────────────┬──────────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────────┐
│  12 bounded contexts, one deployable                             │
│                                                                  │
│   iam ── inventory ── sales ── crm ── procurement ── finance      │
│   hr ── asset ── project ── analytics ── ai ── notification       │
│                                                                  │
│  Each: domain/ · repository/ · service/ · web/ · dto/            │
└───────────────────────────┬──────────────────────────────────────┘
                            │  Spring Data JPA · Hibernate 6.6
┌───────────────────────────▼──────────────────────────────────────┐
│  Neon serverless PostgreSQL 18                                   │
│  41 tables · shared schema · tenant_id discriminator             │
│  Fallback: file-backed H2 (profile "h2") for offline running     │
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. Modular monolith — the central trade

Every top-level package under `com.biashara` is a **bounded context** that maps 1:1
onto a service boundary in the microservice design the product vision describes:

| Package | Would become | Owns |
|---|---|---|
| `iam` | Auth + User service | Tenants, users, roles, permissions, departments, branches, audit, sessions |
| `inventory` | Inventory service | Products, categories, warehouses, movement ledger |
| `sales` | Sales service | Sales, line items, POS checkout |
| `crm` | CRM service | Customers, interaction timeline, RFM scoring |
| `procurement` | Supplier service | Suppliers, purchase orders, reorder suggestions |
| `finance` | Expense + Payment service | Expenses, invoices, payments, accounts, journal |
| `hr` | HR service | Employees, attendance, leave, payroll |
| `asset` / `project` | Asset + Project service | Register, depreciation, projects, tasks |
| `analytics` | Analytics service | Dashboard aggregation, business health |
| `ai` | AI Recommendation service | Insights, assistant |
| `notification` | Notification service | In-app inbox, channel routing |

**Why not microservices now.** Fourteen services means fourteen deployables,
inter-service contracts, distributed transactions across a POS checkout that must be
atomic, and a service mesh — before a single business feature exists. For a system
that has to be run, demonstrated and reasoned about today, that is cost without
return.

**What makes the trade safe.** Extraction later means lifting a package, because the
boundaries are already drawn and cross-context references go through domain types
rather than shared tables. The cost of the split is deferred, not multiplied.

**Where the boundary is knowingly crossed.** `PosService` writes to `sales`,
`inventory`, `finance` and `crm` in one transaction. In a distributed design this
becomes a saga with compensating actions. That is the single largest piece of work in
the split, and it is a genuine trade: today the checkout is atomic and cannot leave
stock inconsistent with the ledger.

---

## 3. Multi-tenancy

**Shared schema with a discriminator column.** One deployment, one set of tables,
every tenant-owned row carrying `tenant_id`.

```java
@MappedSuperclass
public abstract class TenantAwareEntity extends BaseEntity {
    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
}
```

**Three isolation options were available:**

| Approach | Isolation | Cost |
|---|---|---|
| Database per tenant | Strongest | Unworkable connection and migration cost at MSME price points |
| Schema per tenant | Strong | Migrations multiply by tenant count; Postgres degrades past a few thousand schemas |
| **Shared schema + discriminator** | **Enforced in code** | **One migration, one pool — requires discipline** |

Shared schema is the only one that survives thousands of small tenants on a plan an
MSME will pay for. The discipline it requires is centralised in one place:

**The tenant is never a parameter.** It is read from the caller's authenticated
token:

```java
@GetMapping
@PreAuthorize("hasAuthority('inventory.product.view')")
public Page<ProductResponse> products(...) {
    Long tenantId = currentUser.tenantId();   // from the JWT, never the request
    ...
}
```

A request cannot ask for another business's data because **it never gets to name
the business.** There is no `?tenantId=` to tamper with anywhere in the API.

---

## 4. Authorization: permissions, not roles

### The catalogue

63 permissions, dot-delimited `module.entity.action`:

```
inventory.product.create      finance.expense.approve      admin.user.reset_password
sales.pos.operate             hr.payroll.process           platform.tenant.manage
```

### How an endpoint is secured

```java
@PreAuthorize("hasAuthority('finance.expense.approve')")
```

Not `hasRole('FINANCE_MANAGER')`. The difference matters: roles are **database rows
that bundle permissions**, so a business can invent "Weekend Supervisor" at runtime
and it works without a redeploy. Role-based checks hardcode the org chart into the
source.

### The hierarchy is one integer

```java
private Integer hierarchyLevel;   // 0 platform · 10 owner · 20 admin
                                  // 30 GM · 40 dept manager · 50 staff
```

Two rules, both enforced in `UserManagementService` rather than in the form:

```java
// 1. You cannot mint someone at or above your own level.
if (role.getHierarchyLevel() <= actor.getHierarchyLevel()) {
    throw new ForbiddenException(
        "You cannot create a %s — that role is at or above your own level"
            .formatted(role.getName()));
}

// 2. A department-level manager is confined to their own department.
if (actor.getHierarchyLevel() >= DEPARTMENT_SCOPED_FROM_LEVEL
        && actor.getDepartmentId() != null
        && !request.departmentId().equals(actor.getDepartmentId())) {
    throw new ForbiddenException("You can only create users within your own department");
}
```

That single integer is what turns *"managers can only create users within their own
department"* from a sentence in a specification into something a test can prove.

### The frontend is not the boundary

The sidebar is filtered from the same permission set:

```ts
export function visibleNavigation(permissions: string[]): NavSection[] { ... }
```

This is **convenience**. Typing `/finance` as a cashier renders an access-denied
screen *and* the underlying API call returns 403. The UI filter and the server check
are independent, and only the second one is load-bearing.

**One deliberate detail:** a cashier has no dashboard-executive permission, so
routing them to `/dashboard` after sign-in would show a permission error as the
first thing they ever see. `landingRouteFor()` sends each user to the first screen
their role can actually use.

---

## 5. Authentication

```
POST /api/auth/login
   │
   ├── account locked?      → 401, recorded as FAILED_ACCOUNT_LOCKED
   ├── suspended/inactive?  → 401, recorded as FAILED_ACCOUNT_DISABLED
   ├── BCrypt mismatch?     → increment counter; lock at 5 for 15 min; record
   │
   └── success
        ├── clear counter, stamp lastLoginAt + IP
        ├── access token  — JWT, 2h, carries tenant + permissions + level
        ├── refresh token — opaque, 7d, PERSISTED so it can be revoked
        └── audit + login-history rows
```

**Why the access token carries the permission set.** An authenticated request needs
no database round trip to be authorised. The trade is that a permission change only
takes effect on the next token — which is why access tokens are short-lived and
refresh tokens are server-side and revocable. A self-contained refresh token would
be unrevokable, which defeats the purpose of having one.

**Rotation.** Presenting a refresh token retires it as a new one is issued, so a
stolen token is usable at most once.

**Client-side refresh is single-flight:**

```ts
let refreshInFlight: Promise<string> | null = null
```

A dashboard firing eight parallel requests on an expired token triggers **one**
refresh, not eight — eight would rotate the token out from under each other and log
the user out.

### Everything else in the security posture

| Control | Where |
|---|---|
| BCrypt hashing, cost 10 | `SecurityConfig.passwordEncoder` |
| Lockout: 5 attempts / 15 min | `AuthService.registerFailure` |
| Password policy: length, case, digit, symbol | `AuthService.validatePasswordPolicy` |
| Forced first-login change | `User.firstLogin` + router guard |
| Generated temporary passwords | `UserManagementService.generateTemporaryPassword` |
| Sessions revoked on suspend / reset / password change | `RefreshTokenRepository.revokeAllForUser` |
| Audit log on every state change | `AuditService` |
| Login history including unknown emails | `AuthService.recordLogin` |
| Soft delete everywhere | `BaseEntity.deleted` |

Temporary passwords exclude ambiguous characters (`O/0`, `l/1/I`) because they are
read off a screen and typed by hand.

---

## 6. Data layer decisions

### Sequence-backed ids, not `IDENTITY`

```java
@GeneratedValue(strategy = SEQUENCE, generator = "biashara_id_seq")
@SequenceGenerator(name = "biashara_id_seq", sequenceName = "biashara_id_seq",
                   allocationSize = 50)
```

`IDENTITY` forces Hibernate to round-trip for **every single insert** to read the
generated key back, which silently disables JDBC batching. Measured: seeding 90 days
of trading became ~12,000 individual round trips to a US-East database and took
**over 40 minutes**. A pooled sequence hands out 50 ids per call; the same seed now
completes in **about three minutes**.

This is the single highest-leverage data-layer decision in the project, and it is
invisible until you actually run it against a remote database.

### `open-in-view: false`

The Hibernate session closes with the transaction, so DTO mapping cannot lazily
trigger queries from the view layer.

**The cost is real and deliberate:** every association a response needs must be
declared:

```java
@EntityGraph(attributePaths = {"category", "supplier", "warehouse"})
Page<Product> findByTenantIdAndDeletedFalse(Long tenantId, Pageable pageable);
```

Getting this wrong throws `LazyInitializationException` — loudly, at the endpoint.
**Nine endpoints failed this way during development and were fixed by declaring the
missing paths.** With `open-in-view` left on (the Spring Boot default) every one of
those would have silently worked while issuing N+1 queries per row. N+1 becomes a
compile-and-test problem instead of a production incident.

### `prepareThreshold=0` on Neon

Neon's connection pooler multiplexes client connections onto shared server
connections and caches statement plans against them. After a schema change a cached
plan raises:

```
ERROR: cached plan must not change result type
```

Disabling server-side prepared statements removes that failure class, at a small
cost in per-statement planning. This is standard practice against any
PgBouncer-style pooler in transaction mode.

### Aggregation in the database

Trend charts and breakdowns are computed by Postgres, not by loading rows into Java:

```java
@Query(value = """
        select to_char(s.sale_date, 'YYYY-MM-DD') as bucket,
               sum(s.total)                       as value,
               sum(s.total - coalesce(s.cost_of_goods, 0)) as secondary,
               count(*)                           as count
        from sales s
        where s.tenant_id = :tenantId and s.deleted = false
          and s.status = 'COMPLETED' and s.sale_date >= :from
        group by to_char(s.sale_date, 'YYYY-MM-DD')
        order by bucket asc
        """, nativeQuery = true)
List<DailySeriesPoint> dailyRevenueSeries(...);
```

Native because date truncation is not expressible in JPQL, projected onto an
interface so the result is typed. One round trip regardless of sale volume.

---

## 7. Domain modelling that carries business meaning

### Cost of goods is captured per line, at the moment of sale

```java
.unitCost(product.getBuyingPrice())   // snapshot, never re-read
```

Gross profit stays correct forever, even after supplier prices move. Recomputing
historical profit from *today's* cost is the most common way a small-business report
lies.

### The stock ledger reconciles to stock on hand

`Product.currentStock` is a **cached projection** of `inventory_transactions`. Every
change to it writes a movement in the same transaction, carrying the resulting
balance.

The seeder proves the invariant rather than assuming it — it back-calculates the
opening balance:

```
opening = current + sold + wastage − received
```

Walking the movements forward from there lands **exactly** on each product's
recorded stock. Inventory reports and the ledger therefore cannot disagree.

### Snapshots where an audit trail needs them

`AuditLog` stores `actorName` and `targetName` as denormalised copies, not joins. An
audit trail has to stay readable after the user or row it references has been
renamed or deleted. Similarly `SaleItem.productName` — a receipt must reprint
identically years later.

### Scores are computed, never assigned

Customer tier comes from RFM components derived from actual purchase history:

```java
int recency   = daysSince <= 3 ? 5 : daysSince <= 7 ? 4 : ...;
int frequency = orders >= 12 ? 5 : orders >= 8 ? 4 : ...;
int monetary  = percentileScore(sortedSpends, total);   // relative to THIS business
```

Monetary score is a **percentile within the tenant**, not an absolute threshold — a
"VIP" at a kiosk and at a wholesaler are different numbers, and a fixed threshold
would be wrong for one of them.

Supplier reliability is derived from observed delivery dates against agreed lead
times. Sales velocity — and therefore every stockout prediction — is units sold over
the last 30 days.

---

## 8. The business health index

Six weighted components, each scored 0–100 from live figures:

| Component | Weight | Derived from |
|---|---|---|
| Sales growth | 20% | 30 days vs the prior 30 |
| Profit margin | 20% | Gross margin against a 30% retail target |
| Stock availability | 15% | Low and out-of-stock share, out-of-stock penalised 2× |
| Collections | 15% | Receivables as a share of monthly revenue |
| Customer retention | 15% | Share of customers above the churn threshold |
| Supplier reliability | 15% | Mean measured on-time rate |

Each component returns its own `detail` string explaining itself, so the score is
never a bare number the owner has to trust. Flat trade maps to 60 rather than 0 —
"unchanged" is not failure.

---

## 9. The AI layer

### Insights are derived, not authored

`IntelligenceSeeder` generates the insight set by querying the transactions it just
created. "Rice runs out in 3 days" carries `entityType`/`entityId`, so the UI links
straight to the product where the stock level and sales velocity that produced the
figure are visible.

The schema enforces the product thesis: an insight has `title` (what happened),
`cause` (why) and `recommendation` (what to do). All three are required for it to be
worth showing.

### The assistant is rule-based, deliberately

```java
Intent intent = classify(question);          // keyword → intent
AnswerResponse answer = compose(tenantId, thread, question, intent);
```

`compose` runs the analytics queries for that intent and builds the reply from what
came back. It returns:

- `dataPoints` — the actual values used
- `dataSource` — the queries that produced them

**Why not an LLM.** An owner about to reorder stock or chase a debtor has to be able
to check the claim. A rule-based assistant over real queries **cannot invent a
number** because it never generates one. Asked *"Why did profits fall?"* on data
where profit rose, it says profit rose — a model optimised for agreeableness would
explain a decline that never happened.

**The swap point is one method.** Replacing `compose` with a Spring AI call keeps
the query layer as the model's tool surface. The grounding — the hard part — already
exists.

---

## 10. Frontend

**shadcn/ui**, not a component library. Components are **source in the repository**
(`src/components/ui/`), generated by the official CLI and built on Radix primitives
+ CVA + `tailwind-merge`. Owning the source is the point: the `badge` component was
extended with `success`/`warning`/`danger`/`info` variants that an ERP needs, which
would have meant fighting a packaged library's API.

Theme is CSS custom properties in HSL, so light and dark are one variable swap.

**State.** Zustand for auth (small, no provider tree) and a `useApi` hook for
fetching. No query library: for a read-heavy demo the caching it buys does not pay
for the dependency. The hook takes an `enabled` flag so permission-gated panels can
skip a request **without calling the hook conditionally**, which would break the
rules of hooks.

**Charts.** One `Chart` wrapper owns typography, grid weight, tooltip style and the
categorical palette, so every chart reads as one system rather than as library
defaults. Colour assignment follows the data's job — categorical hues in fixed order
for identity, a single hue for magnitude — and delta colour follows *meaning*, not
sign: rising expenses are red, rising revenue green.

**Every list screen** uses one `ResourceTable`, so pagination, search debouncing,
empty states and loading behaviour are identical everywhere instead of reimplemented
per module. A single `DataState` component decides loading vs error vs empty vs
content — which is what stops half the screens spinning forever and the other half
rendering an empty table as though the business genuinely has no data.

---

## 11. Known limits

Stated plainly, in the order they would bite:

1. **Invoice numbers race.** `nextInvoiceNumber()` derives from a row count. Safe on
   one node; two nodes could collide. Wants a per-tenant database sequence.
2. **Schema is `ddl-auto`.** Right for a demo, wrong for production. Wants Flyway
   before the schema stops being disposable.
3. **Analytics hit live tables.** Past a few hundred thousand sales, the dashboard
   wants read replicas or materialised rollups.
4. **Permission changes need a new token.** Up to two hours of staleness, bounded by
   access-token lifetime. Acceptable trade for stateless auth; a revocation list
   would tighten it.
5. **Sales velocity is a flat 30-day mean.** No seasonality or trend weighting, so
   predictions lag a genuine demand shift. Exponential smoothing is the next step.
6. **`SeedResetService` truncates a hardcoded table list.** A new entity must be
   added to it. A metadata-driven sweep would be better but riskier.

---

## 12. Verification performed

Every claim below was checked against the running stack:

| Check | Result |
|---|---|
| Backend compiles | 163 source files, clean |
| Frontend typechecks | `tsc --noEmit`, 0 errors |
| REST endpoints | **65/65 return 200** as Business Owner |
| Seed coverage | **27/27 tables** at or above the 10-row floor |
| RBAC per role | Owner / GM / Cashier / Finance / HR probed; denials match the matrix |
| Privilege escalation | HR Manager → Business Owner returns **403** |
| Department scope | HR Manager → user in Finance returns **403** |
| POS transaction | Stock 64 → 61 with matching `STOCK_OUT` movement, balance and invoice ref |
| Assistant grounding | Correctly refutes a false premise, returns figures + query names |
| Full path | Browser → Vite proxy → Spring Boot → Neon |

The categorical chart palette was validated programmatically against the deck
surface — lightness band, chroma floor, colour-vision-deficiency separation
(worst adjacent pair ΔE 26.8), normal-vision separation (ΔE 31.8) and 3:1 contrast
all pass. Not eyeballed.
