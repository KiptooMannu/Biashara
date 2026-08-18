# Neon to Supabase PostgreSQL Migration & Performance Optimization Plan

## Migration Date: 2026-08-17

### Project: BIASHARA ERP - AI-powered multi-tenant ERP for MSMEs

---

## PHASE 1: ASSESSMENT FINDINGS

### Current Architecture
- **Primary Database**: Neon PostgreSQL (serverless)
- **Fallback Database**: H2 (local file-based)
- **ORM**: Spring Data JPA with Hibernate 3.5.6
- **Connection Pool**: HikariCP (10 max connections, 2 min idle)
- **Java Target**: 21 LTS (bytecode compiled to Java 21)
- **Authentication**: JWT with HS512 signing
- **Multi-tenancy**: Implemented via `tenant_id` column in entities
- **Schema Management**: Hibernate `ddl-auto: update` (not Flyway)

### Entities & Relationships
- **62 entity files** with multiple @ManyToOne, @OneToMany relationships
- **EntityGraph usage**: Found in 15+ repositories for eager loading specific relationships
- **FetchType configuration**: Mostly LAZY (good practice)
- **Batch settings**: `batch_size: 100`, `order_inserts: true` (optimal for throughput)

### Performance Architecture
- **Dashboard Service**: Uses database aggregations (COUNT, SUM, AVG, GROUP BY) - GOOD
- **Analytics**: Projections used instead of full entity loading - GOOD
- **Pagination**: Found PageRequest usage in some repositories
- **Issue**: Notification endpoint returns 500 error (likely database connection issue with Neon credentials)

### Current Database Performance Issues
1. **Neon max compute limit exceeded** - likely due to:
   - Missing indexes on `tenant_id`, `user_id`, `customer_id`, `product_id`, etc.
   - Potential N+1 queries in some services
   - Full table scans on large tables
   - Inefficient pagination (missing LIMIT clauses or high offsets)
   - Possible entity over-fetching in some repositories

2. **Connection pool exhaustion** - Neon's connection pooling limitations
   - Current HikariCP settings may be inadequate for high concurrency
   - Should use Supabase Transaction Pooler instead of direct connection

---

## PHASE 2: MIGRATION STRATEGY

### Step 1: Configuration Changes (Non-Breaking)
1. **Add Supabase profile** (`application-supabase.yml`)
2. **Update .env with Supabase credentials** (Transaction Pooler for app connections)
3. **Update .env.example with Supabase examples**
4. **No code changes required** - JPA/Hibernate abstraction handles PostgreSQL compatibility

### Step 2: Data Migration (Zero-Downtime)
1. **Use `pg_dump` from Neon to backup schema + data**
2. **Use `pg_restore` into Supabase**
3. **Verify data integrity** - row counts, foreign keys, constraints
4. **No schema modification needed** - both are PostgreSQL

### Step 3: Performance Optimization
1. **Add missing indexes** on frequently queried columns
2. **Review and optimize slow queries** using EXPLAIN ANALYZE
3. **Fix N+1 queries** in service layer
4. **Implement proper pagination** with cursor-based or limit+offset
5. **Optimize connection pooling** for Supabase

---

## PHASE 3: DETAILED MIGRATION STEPS

### Create application-supabase.yml
```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  datasource:
    # Use Supabase Transaction Pooler for better connection efficiency
    # Direct connection (port 5432) for migrations and admin tasks only
    url: ${BIASHARA_DB_URL}
    username: ${BIASHARA_DB_USER}
    password: ${BIASHARA_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      # Supabase typically responds faster than Neon
      connection-timeout: 10000
      idle-timeout: 300000
      max-lifetime: 900000
      # Connection pooler settings
      connection-init-sql: "SET session_preload_libraries=''"
```

### Update .env File
```bash
# For Supabase Transaction Pooler (recommended for application)
BIASHARA_DB_URL=postgresql://postgres.kdwjmcqiavdwnjigelsn:[PASSWORD]@aws-0-eu-north-1.pooler.supabase.com:6543/postgres

# For direct connection (migrations, admin tasks only)
# BIASHARA_DB_URL=postgresql://postgres:[PASSWORD]@db.kdwjmcqiavdwnjigelsn.supabase.co:5432/postgres
```

### Prepare for Data Migration
1. Stop the BIASHARA application
2. Verify all data is persisted in Neon
3. Create Supabase PostgreSQL instance
4. Test connectivity to Supabase

### Execute Data Migration
```bash
# From local machine or CI/CD pipeline
# Step 1: Backup from Neon
pg_dump -h ep-ancient-firefly-ayi4knqn-pooler.c-5.us-east-2.aws.neon.tech \
  -U neondb_owner \
  -d neondb \
  --no-password > biashara_backup.sql

# Step 2: Restore to Supabase
psql -h db.kdwjmcqiavdwnjigelsn.supabase.co \
  -U postgres \
  -d postgres \
  -f biashara_backup.sql

# Step 3: Verify
psql -h db.kdwjmcqiavdwnjigelsn.supabase.co \
  -U postgres \
  -d postgres \
  -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';"
```

---

## PHASE 4: PERFORMANCE OPTIMIZATION

### Index Strategy
Based on query patterns identified in DashboardService, ExpenseRepository, InvoiceRepository, etc.:

```sql
-- Multi-tenant filtering (CRITICAL - every query includes tenant_id)
CREATE INDEX idx_tenant_id ON [table_name] (tenant_id);

-- User-specific queries
CREATE INDEX idx_user_tenant ON user (tenant_id, id);
CREATE INDEX idx_created_by ON [table_name] (created_by, tenant_id);

-- Common filtering
CREATE INDEX idx_deleted_tenant ON [table_name] (deleted, tenant_id);
CREATE INDEX idx_status_tenant ON [table_name] (status, tenant_id);
CREATE INDEX idx_created_on_tenant ON [table_name] (created_on DESC, tenant_id);

-- Specific to high-volume queries
CREATE INDEX idx_invoice_customer_tenant ON invoice (customer_id, tenant_id);
CREATE INDEX idx_sale_customer_tenant ON sale (customer_id, tenant_id);
CREATE INDEX idx_payment_invoice ON payment (invoice_id);
CREATE INDEX idx_inventory_product ON inventory_transaction (product_id, tenant_id);
CREATE INDEX idx_expense_dept ON expense (department_id, tenant_id);
CREATE INDEX idx_employee_tenant ON employee (tenant_id, deleted);
```

### Query Optimization Pattern
**BEFORE (potential N+1 issue):**
```java
List<Invoice> invoices = invoiceRepository.findByTenant(tenantId);
invoices.forEach(inv -> {
    Customer cust = inv.getCustomer(); // N database calls!
    Total += inv.getAmount();
});
```

**AFTER (optimized):**
```java
@Query("""
  SELECT new com.biashara.finance.dto.InvoiceSummary(
    i.id, i.invoiceNumber, i.amount, c.name)
  FROM Invoice i
  JOIN i.customer c
  WHERE i.tenant.id = :tenantId
  AND i.deleted = false
""")
List<InvoiceSummary> findSummariesByTenant(@Param("tenantId") Long tenantId);
```

### Pagination Pattern
**BEFORE (inefficient offset):**
```java
Page<Invoice> findByTenantId(Long tenantId, Pageable pageable);
// Problem: offset 1000 scans 1000+ rows before returning 20
```

**AFTER (cursor-based pagination):**
```java
@Query("""
  SELECT i FROM Invoice i
  WHERE i.tenant.id = :tenantId
  AND i.deleted = false
  AND i.id > :lastId
  ORDER BY i.id ASC
""")
List<Invoice> findByTenantIdCursor(
    @Param("tenantId") Long tenantId,
    @Param("lastId") Long lastId,
    Pageable pageable);
```

---

## PHASE 5: TESTING CHECKLIST

### Functional Testing
- [ ] Login with demo accounts works
- [ ] Dashboard loads and displays KPIs
- [ ] All CRUD operations work
- [ ] Notifications endpoint returns 200 OK
- [ ] Multi-tenancy isolation works
- [ ] JWT authentication and refresh tokens work
- [ ] Pagination on large datasets works
- [ ] Reports and analytics generate correctly
- [ ] Batch operations (imports, calculations) complete

### Performance Testing
- [ ] Dashboard loads in < 2 seconds
- [ ] API endpoints respond in < 500ms average
- [ ] Database queries shown in logs are < 100ms
- [ ] Connection pool stays below 5 active connections
- [ ] No "max compute" errors in Supabase console
- [ ] Concurrent requests (10+) handled correctly

### Data Integrity Testing
- [ ] Row counts match Neon to Supabase
- [ ] Foreign key relationships intact
- [ ] Constraints enforced
- [ ] Unique indexes working
- [ ] Audit logs preserved
- [ ] Tenant isolation maintained

---

## PHASE 6: DEPLOYMENT CHANGES

### For Render.io Deployment
```bash
SPRING_PROFILES_ACTIVE=supabase
BIASHARA_DB_URL=postgresql://postgres.kdwjmcqiavdwnjigelsn:[PASSWORD]@aws-0-eu-north-1.pooler.supabase.com:6543/postgres
BIASHARA_DB_USER=postgres.kdwjmcqiavdwnjigelsn
BIASHARA_DB_PASSWORD=[SECURE_PASSWORD]
BIASHARA_JWT_SECRET=[SECURE_SECRET_KEY]
```

### For Vercel Frontend Deployment
```bash
VITE_API_BASE_URL=https://[render-backend-url]/api
```

---

## RISK MITIGATION

1. **Backup Strategy**
   - Full backup before migration
   - Keep Neon instance running for 48 hours after migration (fallback)
   - Regular automated backups with Supabase

2. **Rollback Plan**
   - If issues occur, revert Spring profile to `neon`
   - Keep both databases in sync for 24 hours during testing

3. **Monitoring**
   - Enable Supabase database monitoring
   - Track connection pool metrics
   - Monitor query performance with slow query logs

---

## ESTIMATED TIMELINE

| Phase | Task | Duration |
|-------|------|----------|
| 1 | Configuration changes | 30 min |
| 2 | Data migration & verification | 1-2 hours |
| 3 | Application testing (functional) | 1-2 hours |
| 4 | Performance optimization | 2-4 hours |
| 5 | Load testing & verification | 1-2 hours |
| 6 | Deployment & monitoring | 30 min |
| **TOTAL** | | **6-11 hours** |

---

## SUCCESS CRITERIA

✅ All data migrated successfully
✅ Dashboard loads in < 2 seconds
✅ No "max compute exceeded" errors
✅ All API endpoints respond correctly
✅ Multi-tenancy works as before
✅ Authentication & JWT work
✅ Connection pool stable
✅ Zero business data loss
