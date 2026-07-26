# Demo script

A guided walkthrough for judging. Every step says **what to click**, **what you
should see**, and **why it matters** — so nothing depends on the presenter
remembering a talking point.

- **Short version:** Acts 1–4, about 5 minutes.
- **Full version:** all seven acts, about 12 minutes.
- **If you are being judged on a timer,** do Acts 1, 2, 3 and 6. Those four carry
  the argument.

---

## Before you start

### 1. Start the two processes

Two terminals, from the project root.

```bash
# Terminal 1 — the API. Wait for "Started BiasharaApplication".
cd backend
mvn spring-boot:run

# Terminal 2 — the web app. Wait for "ready in ...".
cd frontend
npm install        # first run only
npm run dev
```

The database is Neon serverless Postgres and is **already seeded** — there is no
migration step, no local database to install, and no import to run.

### 2. Confirm it is healthy before you present

The backend prints a table on every boot. You want to see this line:

```
All 27 tables have at least 10 rows.
```

followed by the credential list. If a table were empty, it would be flagged
`<-- BELOW MINIMUM` right there — the point being that an empty screen is caught in
a log line rather than in front of a judge.

> **Cold start:** Neon scales to zero when idle, so the very first request after a
> quiet period can take a few seconds. Load the dashboard once before you present.

### 3. Open two browser tabs

| Tab | URL | Used in |
|---|---|---|
| The application | http://localhost:5173 | Acts 1–7 |
| Swagger UI | http://localhost:8080/swagger-ui.html | Act 7 |

You do not need to type any credentials. The sign-in screen lists every seeded
account with an **Enter** button.

---

## Act 1 — The owner's dashboard *(90 seconds)*

**Goal:** establish that this is a real ERP with real data, and that its headline
feature is reasoning rather than reporting.

### Steps

1. On the sign-in screen, find the **Business Owner** card and click **Enter**.
2. You land on the dashboard. Let it finish loading before talking.
3. Point at the **AI insights** block — it sits directly under the KPI row,
   deliberately above the charts.
4. Read one insight aloud, following its three parts: the **title** (what
   happened), the **Why** box, and the **Do this** box.
5. Scroll to **Business health** on the right and expand on the six components.

### What you should see

- **14 KPI tiles.** Monthly revenue ≈ KES 2.38M, gross profit ≈ 735K, operating
  expenses ≈ 502K, and a **positive** net position ≈ +234K.
- **A business health score around 93.7 — "Excellent"**, broken into six weighted
  components each showing the figures behind it.
- Insights with concrete numbers, not slogans: *"Cooking Oil 2L runs out in 2.4
  days"*, *"Payroll of KES 1,061,000 falls due this month"*.

### Why it matters — say this

> "Every tile has a period-on-period comparison, because a number without a
> direction is not information. And the delta colour follows the *meaning*, not the
> sign — rising expenses are red, rising revenue is green."

> "The health score is not a badge. It is six measured ratios — sales growth,
> margin, stock availability, collections, retention, supplier reliability — each
> weighted, each showing its own evidence. You can see exactly why it is 93 and not
> 78."

### If a judge pushes back

> *"Is this data real or hardcoded?"*
> "It is generated, but it is internally consistent rather than decorative. Sales
> follow weekday, weekend and month-end salary patterns. Customer tiers and churn
> risk are computed from the purchase history. Sales velocity — and therefore every
> stockout prediction — is derived from the last 30 days of actual sales. Nothing on
> this screen is a literal, and I can show you the query behind any of it."

---

## Act 2 — The AI assistant *(90 seconds)*

**Goal:** show the differentiator, and show that it is trustworthy.

### Steps

1. Click **AI Assistant** in the sidebar (under Overview).
2. Click the suggested prompt **"Why did profits fall?"**.
3. Wait for the answer. **Do not skip the grey box underneath it.**
4. Expand the **Figures used** panel and read the `source:` line aloud.
5. Optionally ask a second question: **"What should I reorder?"**

### What you should see

The assistant **disagrees with the question**. Profits did not fall — it reports
gross profit is *up* about KES 107,000, with both periods' figures. Underneath, a
panel lists the exact values it used and names the queries:

```
source: sales.sumGrossProfitBetween + expenses.sumBetween + products.countOutOfStock
```

### Why it matters — say this

> "Notice it contradicted me. I asked why profits fell; they had not. A model
> optimised to be agreeable would have invented an explanation for a decline that
> never happened — and the owner would have acted on it."

> "This is deliberately **not** a language model. It classifies the question,
> runs the analytics queries that answer it, and composes the reply from what came
> back. It cannot hallucinate a number because it never generates one. And it hands
> you the figures and the query names, so a shopkeeper about to spend money can
> check the claim."

> "One method — `AiAssistantService.compose()` — is the swap point for Spring AI.
> The query layer is already built; it becomes the model's tool surface. The
> grounding is the hard part and it exists."

### If a judge pushes back

> *"So there's no real AI?"*
> "There is no LLM, and I would rather say that plainly than imply one. What is
> here is the part that determines whether AI output is usable: grounded,
> traceable, auditable answers over live business data. Bolting a model on top is
> an afternoon; making its answers checkable is the work."

---

## Act 3 — Role-based access control *(2 minutes)*

**Goal:** prove the security model is enforced, not decorative. **This is the act
most likely to win technical judges.**

### Steps

1. Click your avatar (top right) → **Sign out**.
2. Sign in as **POS Cashier** (`cashier@biashara.demo`).
3. **Point at the sidebar.** It has collapsed from sixteen destinations to four:
   Dashboard, Point of Sale, Sales, Customers.
4. Now the important part. **Type a forbidden URL directly** into the address bar:
   ```
   http://localhost:5173/finance
   ```
5. You get an access-denied screen naming the role. **Then open the browser
   devtools Network tab** and reload — the API call returns **403**.
6. Sign out. Sign in as **HR Manager** (`hr@biashara.demo`).
7. Note the sidebar is different again: HR, Reports, Business Intelligence — but
   **no Inventory, no Sales, no Finance**.

### What you should see

| Role | Permissions | Sidebar |
|---|---|---|
| Business Owner | **61** | Everything (16 destinations) |
| General Manager | **31** | Operations + reports; no Finance, Audit or Settings |
| POS Cashier | **7** | Dashboard, POS, Sales, Customers |
| HR Officer | **5** | Dashboard, HR |

### Why it matters — say this

> "The cashier's sidebar is filtered from the permissions in their token. But
> hiding a menu item is *convenience* — it is not the security boundary. Watch."
>
> *(type /finance directly)*
>
> "The server refuses it. Every endpoint declares its own permission:
> `@PreAuthorize("hasAuthority('finance.view')")`. There are 63 of them, and they
> are checked on every request regardless of what the UI is showing."

> "It is permission-based, not role-based. Roles are just database rows that bundle
> permissions — so a business can invent a new role at runtime without a code
> change or a redeploy."

---

## Act 4 — A sale that moves everything *(60 seconds)*

**Goal:** show transactional integrity — the thing that separates an ERP from a
CRUD app.

### Steps

1. Still signed in as the cashier, click **Point of Sale**.
2. **Note the stock badge** on the first product tile before you touch it.
3. Click that product tile **three times**. Watch the basket, VAT and total update.
4. Leave the customer as *Walk-in*, payment method as *M-Pesa*.
5. Click **Complete sale**. A toast confirms the invoice number.
6. Navigate to **Inventory** and find the same product.
7. Click into it and look at the **movement history**.

### What you should see

Stock has dropped by exactly three. In the movement ledger there is a new
`STOCK_OUT` row for quantity 3, carrying the **resulting balance** and the
**invoice number** of the sale you just made.

### Why it matters — say this

> "One request did six things in a single transaction: recorded the sale with its
> line items, decremented stock, wrote the movement to the ledger, recorded the
> payment, updated the customer's loyalty points, and wrote an audit entry."

> "`Product.currentStock` is a cached projection of the movement ledger. Every
> change to it writes a movement in the same transaction, so the two can never
> drift. If any part fails — insufficient stock, over the credit limit — none of it
> happens."

### Optional 20-second addition

Try to sell **more units than exist**. The server refuses with the actual number in
stock. Business rules live on the server, not in the form.

---

## Act 5 — User management and the hierarchy *(2 minutes)*

**Goal:** show the enterprise user-creation model from the specification, properly
enforced.

### Steps

1. Sign out, sign in as **HR Manager** (`hr@biashara.demo`).
2. Go to **Users & Roles** → the **Roles & permissions** tab.
3. Point out the level numbers and the badges: some roles read **"You can assign"**,
   others **"Above your level"**.
4. Click **Add user**. Open the **Role** dropdown.
5. **Only five roles are listed** — every one below level 40.
6. Fill in a name and email, pick **HR Officer**, set Department to **Human
   Resources**, and submit.
7. A dialog shows a **generated temporary password**. Copy it.
8. Go to the **Invitations** tab and find the new invitation with its rendered
   email body.
9. Sign out. Sign in as the **new user** with that temporary password.
10. You are **trapped on the change-password screen** until the policy is met.

### What you should see

- The role dropdown cannot offer a senior role — the escalation path is closed in
  the UI *and* on the server.
- A generated password like `aLWMC!8o` — the administrator never chooses it.
- The new account has `firstLogin = true` and status `PENDING_INVITATION`.
- The change-password screen ticks off five live requirements as you type.

### Why it matters — say this

> "There is no public sign-up, by design. The founding owner is seeded with the
> business; every other account is created by an authorised administrator — which is
> how Microsoft 365, SAP and Odoo actually work."

> "Two rules are enforced server-side, not in the form. First, hierarchy: you can
> only create or assign a role strictly below your own level. Second, department
> scope: a department manager can only place users in the department they head.
> That single integer is what makes 'managers can only create users in their own
> department' enforceable instead of aspirational."

> "The administrator never chooses someone else's password. The system generates
> one, stores only the BCrypt hash, and forces a change at first login."

### The provable version — if a judge is sceptical

Ask them to watch, then run this in a terminal. It attempts a privilege escalation
and a cross-department creation as the HR Manager:

```bash
# Both return 403 with an explanatory message
curl -s -X POST http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer $HR_TOKEN" -H 'Content-Type: application/json' \
  -d '{"firstName":"Rogue","lastName":"Test","email":"r@x.demo","roleId":<BUSINESS_OWNER_ID>}'
```

Verified responses:

```
403 — You cannot create a Business Owner — that role is at or above your own level
403 — You can only create users within your own department
```

---

## Act 6 — The audit trail *(45 seconds)*

**Goal:** close the loop. Everything they just watched is on the record.

### Steps

1. Sign out, sign back in as **Business Owner**.
2. Go to **Audit Trail**.
3. Find the entries from this demo: the sale, the user creation, the sign-ins.
4. Switch to the **Sign-in history** tab.

### What you should see

Every state-changing action with actor, role, target, details, IP and timestamp —
including the user you just created and every sign-in during the demo. The sign-in
tab shows successes *and* failures.

### Why it matters — say this

> "Actor and target names are stored as snapshots, not joins — an audit trail has to
> stay readable after the record it references is renamed or deleted."

> "Failed sign-ins are recorded even when the email matches no account, so
> credential stuffing is visible rather than silently discarded. And accounts lock
> for fifteen minutes after five failures."

---

## Act 7 — The API surface *(45 seconds, for technical judges)*

**Goal:** show the depth behind the UI.

### Steps

1. Switch to the Swagger tab: http://localhost:8080/swagger-ui.html
2. Show the grouped tags: Authentication, Dashboard, Inventory, Sales & POS, CRM,
   Procurement, Finance, HR, Assets & Projects, Reports, AI, User Management,
   Audit, Notifications.
3. Expand **Authentication → POST /api/auth/login**, click **Try it out**, and sign
   in with the owner credentials.
4. Copy the `accessToken`, click **Authorize** at the top, paste it.
5. Now call any endpoint live — try **GET /api/procurement/reorder-suggestions**.

### What you should see

65 documented endpoints, callable from the browser. The reorder suggestions come
back with a `rationale` field explaining each one in plain language.

### Why it matters — say this

> "Every endpoint is documented and callable. The reorder suggestion is a good
> example of the general approach: it is not a fixed threshold. It is stock divided
> by measured sales velocity, compared against that supplier's actual lead time, and
> it explains itself in the response."

---

## Questions you should expect

**"How is this multi-tenant if there's one business?"**
> Shared-schema multi-tenancy with a tenant discriminator. Every tenant-owned row
> extends `TenantAwareEntity`, every repository query is scoped by tenant, and the
> tenant comes from the caller's token — never from a request parameter. A request
> cannot ask for another business's data because it never gets to name the business.
> One seeded business demonstrates the model; adding a second is a row, not a
> refactor.

**"Why a monolith and not microservices?"**
> It is a modular monolith and the choice is deliberate. Each of the twelve
> top-level packages is a bounded context that maps 1:1 onto a service boundary in
> the target design. Extracting a service later means lifting a package, not
> untangling a codebase. For a system that has to be run and reasoned about today,
> one deployable is the right trade — with the seams already drawn so the split
> stays cheap.

**"What would break at scale?"**
> Three things, in order. Invoice numbers are derived from a row count, which races
> on multiple nodes — that wants a per-tenant database sequence. The schema is
> managed by Hibernate `ddl-auto`, which is right for a demo and wrong for
> production; that wants Flyway. And the analytics queries hit live tables — past a
> few hundred thousand sales they want either read replicas or materialised
> rollups.

**"What did you not build?"**
> Kafka, Elasticsearch, Redis, SMTP/SMS/WhatsApp gateways, OAuth2, a real 2FA OTP
> channel, Cloudinary storage, receipt OCR, offline sync, payment gateways, Flyway,
> and a drag-and-drop report builder. It is all listed in the README. Invitations
> are persisted and surfaced in the admin UI instead of emailed — which is
> precisely what makes the first-login flow demonstrable rather than theoretical.

---

## Recovery

| Symptom | Fix |
|---|---|
| First request hangs 5–10s | Neon cold start. Load any page once before presenting. |
| `Cannot reach the server` in the UI | Backend not up. Check terminal 1 for `Started BiasharaApplication`. |
| Port 8080 already in use | An earlier run is still alive: `Get-Process java \| Stop-Process -Force` |
| Data looks wrong or partial | `mvn spring-boot:run -Dspring-boot.run.arguments=--biashara.seed.reset=true` (~3 min) |
| No internet in the venue | `mvn spring-boot:run -Dspring-boot.run.profiles=h2` — runs on a local file-backed database instead of Neon. **Seed once on this profile before the venue.** |
| Signed in as the wrong role | Avatar → Sign out. The sign-in screen remembers nothing. |

### The one thing to do beforehand

Run the **h2 profile once** on the machine you will present from, so a local
database exists and seeded. If the venue wifi fails, that is your fallback and it
needs no network at all.
