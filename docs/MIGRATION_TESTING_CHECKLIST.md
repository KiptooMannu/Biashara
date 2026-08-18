# BIASHARA: Neon to Supabase Migration - Testing & Verification Checklist

**Migration Date**: 2026-08-17
**Target Database**: Supabase PostgreSQL
**Source Database**: Neon PostgreSQL

---

## PRE-MIGRATION CHECKLIST

### Data Backup & Verification
- [ ] Neon database backup created and verified
- [ ] Backup file size documented
- [ ] Backup location: `./backend/database-backups/biashara-neon-backup.sql`
- [ ] Backup integrity verified
- [ ] Neon database still accessible (for rollback)

### Configuration Preparation
- [ ] `.env` file updated with Supabase credentials
- [ ] Supabase connection parameters verified
- [ ] JWT secret configured (or kept as demo value)
- [ ] `application-supabase.yml` created with correct settings
- [ ] All credentials stored securely (not in Git)

### Environment Setup
- [ ] PostgreSQL client tools installed (pg_dump, psql)
- [ ] PowerShell migration script reviewed
- [ ] Sufficient disk space for backup operations
- [ ] Network connectivity to both Neon and Supabase verified

---

## MIGRATION EXECUTION CHECKLIST

### Data Migration
- [ ] Backup created from Neon
  - Command: `pg_dump -h ... > biashara-neon-backup.sql`
  - File size: _____ MB
  - Execution time: _____ minutes
  
- [ ] Backup uploaded to Supabase
  - Command: `psql -h ... < biashara-neon-backup.sql`
  - Execution time: _____ minutes
  - Exit code: 0 (success)

- [ ] Data integrity verification
  - [ ] Table count matches
  - [ ] Foreign key constraints present
  - [ ] Unique constraints present
  - [ ] Sample row counts verified

### Database Objects Verification
- [ ] All tables created: `SELECT COUNT(*) FROM information_schema.tables` = _____ tables
- [ ] All views created (if any)
- [ ] All sequences created
- [ ] All triggers created (if any)
- [ ] All functions created (if any)

### Sample Data Checks
Execute these checks in Supabase:

```sql
-- Tenant count
SELECT COUNT(*) FROM tenant;
Expected: _____ tenants

-- User count by role
SELECT role, COUNT(*) FROM user GROUP BY role;
Expected: _____ users total

-- Invoice count
SELECT COUNT(*) FROM invoice;
Expected: _____ invoices

-- Sale count
SELECT COUNT(*) FROM sale;
Expected: _____ sales

-- Customer count
SELECT COUNT(*) FROM customer;
Expected: _____ customers

-- Product inventory
SELECT COUNT(*) FROM product;
Expected: _____ products
SELECT SUM(stock) FROM product;
Expected: _____ total stock items
```

Results documented:
- [ ] Tenant count: _____
- [ ] User count: _____
- [ ] Invoice count: _____
- [ ] Sale count: _____
- [ ] Customer count: _____
- [ ] Product count: _____
- [ ] Total inventory: _____

---

## FUNCTIONAL TESTING CHECKLIST

### Authentication & Authorization
- [ ] Login with demo account (Cashier) successful
- [ ] Login with demo account (Manager) successful
- [ ] Login with demo account (Admin) successful
- [ ] Login with invalid credentials fails appropriately
- [ ] Logout works correctly
- [ ] Session persists after page refresh
- [ ] JWT token refresh works
- [ ] Role-based access control enforced
- [ ] Tenant isolation maintained (users see only their data)

### Dashboard & Analytics
- [ ] Dashboard page loads within 2 seconds
- [ ] All KPI cards display correctly:
  - [ ] Today's sales
  - [ ] Sales trend (month-over-month)
  - [ ] Profit margin
  - [ ] Top customers
  - [ ] Inventory health
  - [ ] Collections status
- [ ] Dashboard charts render:
  - [ ] Daily revenue trend
  - [ ] Revenue by category
  - [ ] Revenue by payment method
  - [ ] Revenue by branch
  - [ ] Top products by revenue
  - [ ] Customer growth
  - [ ] Inventory movement
  - [ ] Business health score
- [ ] All analytics calculations accurate
- [ ] No console errors (F12 developer tools)
- [ ] Business health report visible and accurate

### Core CRUD Operations

#### Invoices
- [ ] Create new invoice
- [ ] Invoice number auto-generated correctly
- [ ] Customer selection works
- [ ] Add line items works
- [ ] Calculate totals correctly (subtotal, tax, total)
- [ ] Edit invoice details
- [ ] List invoices with pagination
- [ ] Filter invoices by status
- [ ] Search invoices by number
- [ ] Mark invoice as paid
- [ ] Delete/archive invoice
- [ ] Invoice PDF generation (if applicable)

#### Sales/POS
- [ ] Create new sale/POS transaction
- [ ] Add products to sale
- [ ] Calculate discounts correctly
- [ ] Calculate tax correctly
- [ ] Process payment
- [ ] Generate receipt
- [ ] List sales with pagination
- [ ] Filter sales by date range
- [ ] View sale details
- [ ] Edit sale (if applicable)

#### Customers
- [ ] Create new customer
- [ ] Customer code auto-generated or assigned
- [ ] Edit customer details
- [ ] View customer history (sales, payments, interactions)
- [ ] List customers with pagination
- [ ] Search customers by name/code
- [ ] Delete/archive customer
- [ ] Customer interactions log works

#### Inventory
- [ ] Create new product
- [ ] Product SKU validates (unique per tenant)
- [ ] Set pricing (cost, selling price)
- [ ] Set stock levels and minimum threshold
- [ ] Edit product details
- [ ] List products with pagination
- [ ] Filter products by category
- [ ] View inventory transactions (movements)
- [ ] Adjust stock levels
- [ ] Stock alerts for low inventory items
- [ ] Archive product

#### Expenses
- [ ] Create new expense
- [ ] Select department/category
- [ ] Expense requires approval
- [ ] Manager can approve/reject expenses
- [ ] List expenses with pagination
- [ ] Filter by status (pending, approved, rejected)
- [ ] View expense trend
- [ ] Expense breakdown charts

#### Employees & HR
- [ ] Create new employee
- [ ] Assign to department/branch
- [ ] Set salary/compensation
- [ ] Record attendance
- [ ] Generate payroll
- [ ] Track leave requests
- [ ] View employee performance/sales (if applicable)

#### Purchases/Procurement
- [ ] Create purchase order
- [ ] Add products and quantities
- [ ] Calculate totals
- [ ] Set delivery date
- [ ] Record payment
- [ ] Update inventory when received

### Reporting & Data Export (if applicable)
- [ ] Run sales report for date range
- [ ] Run inventory report
- [ ] Run revenue by category report
- [ ] Run AR/receivables report
- [ ] Export reports to CSV/Excel
- [ ] Print reports to PDF

### Multi-Tenancy Verification
- [ ] Create multiple tenants
- [ ] Verify user cannot access other tenant's data
- [ ] Verify invoices are isolated by tenant
- [ ] Verify dashboard shows only tenant's data
- [ ] Verify reports show only tenant's data
- [ ] Switch between tenants (if admin user)

### User Management (Admin)
- [ ] Create new user in tenant
- [ ] Assign roles to user
- [ ] Assign permissions to user
- [ ] Edit user profile
- [ ] Disable/enable user account
- [ ] Reset user password
- [ ] View user activity logs
- [ ] Revoke user permissions

---

## PERFORMANCE TESTING CHECKLIST

### Load Time Benchmarks
| Operation | Target | Actual | Pass/Fail |
|-----------|--------|--------|-----------|
| Dashboard load | < 2 sec | _____ sec | ☐ |
| Invoice list (100 items) | < 500 ms | _____ ms | ☐ |
| Create invoice | < 1 sec | _____ sec | ☐ |
| POS transaction | < 500 ms | _____ ms | ☐ |
| Report generation (30 days) | < 2 sec | _____ sec | ☐ |
| Search customers (1000+ records) | < 200 ms | _____ ms | ☐ |

### Database Performance
- [ ] No slow query warnings in logs (> 1000ms)
- [ ] Connection pool stays below 5 connections
- [ ] No "max compute exceeded" errors
- [ ] Query execution times < 100ms typical
- [ ] No N+1 query patterns in logs

### Memory & Resource Usage
- [ ] Application memory usage stable (no leaks)
- [ ] Database connection pool doesn't exhaust
- [ ] CPU usage remains under 50%
- [ ] Network throughput reasonable

### Concurrent User Simulation
- [ ] 5 concurrent users browsing dashboard - ☐
- [ ] 5 concurrent users doing transactions - ☐
- [ ] 5 concurrent users creating reports - ☐
- [ ] No timeouts or errors under moderate load

---

## DATA INTEGRITY TESTING

### Foreign Key Constraints
- [ ] All invoice.customer_id references valid customers
- [ ] All sale.customer_id references valid customers
- [ ] All payment.invoice_id references valid invoices
- [ ] All expense.created_by references valid users
- [ ] All employee references valid departments

### Unique Constraints
- [ ] Email addresses unique per tenant
- [ ] Invoice numbers unique per tenant
- [ ] Sale numbers unique per tenant
- [ ] Product SKUs unique per tenant
- [ ] Customer codes unique per tenant

### Business Logic Validation
- [ ] Invoice totals = sum of line items + tax - discount
- [ ] Sale totals calculated correctly
- [ ] Inventory movements tracked accurately
- [ ] Stock levels updated correctly after sales
- [ ] Payroll calculations correct
- [ ] Revenue calculations match invoice/sale data

### Date/Time Integrity
- [ ] All timestamps in UTC or correct timezone
- [ ] Date calculations correct (month-over-month, YoY)
- [ ] Fiscal period calculations correct
- [ ] No date-related anomalies

---

## SECURITY TESTING CHECKLIST

### Credential Security
- [ ] `.env` file not committed to Git
- [ ] Database password not visible in logs
- [ ] JWT secret not exposed in frontend
- [ ] API keys/tokens not hardcoded
- [ ] No sensitive data in error messages
- [ ] No sensitive data in console logs

### Authentication & Authorization
- [ ] JWT tokens expire correctly
- [ ] Expired tokens rejected
- [ ] Invalid tokens rejected
- [ ] Role-based access enforced
- [ ] Users cannot escalate privileges
- [ ] Deleted users cannot access application

### API Security (if applicable)
- [ ] All endpoints require authentication
- [ ] CORS properly configured
- [ ] SQL injection attempts blocked
- [ ] XSS attempts blocked
- [ ] CSRF protection enabled
- [ ] Input validation on all endpoints

---

## MIGRATION ROLLBACK CHECKLIST (If Needed)

- [ ] Neon database still accessible
- [ ] Neon database has all original data
- [ ] Revert `.env` to Neon configuration
- [ ] Revert `SPRING_PROFILES_ACTIVE` to `neon`
- [ ] Application restarts successfully
- [ ] All functionality works after rollback
- [ ] Confirm business operations unaffected

---

## POST-MIGRATION MONITORING (First 48 Hours)

### Supabase Console Monitoring
- [ ] Monitor database connections (should stay low)
- [ ] Monitor CPU usage (should stay under 50%)
- [ ] Monitor storage usage
- [ ] Monitor query performance
- [ ] Check error logs for anomalies
- [ ] Verify backups are scheduled

### Application Monitoring
- [ ] Check application logs for errors
- [ ] Monitor API response times
- [ ] Track user sessions
- [ ] Monitor feature usage patterns
- [ ] Alert on any exceptions or errors

### User Feedback
- [ ] Collect feedback from administrators
- [ ] Collect feedback from regular users
- [ ] Address any reported issues
- [ ] Monitor for unusual behavior patterns

---

## SIGN-OFF & APPROVAL

### Technical Verification
- Verified by: _____________________
- Date: _____________________
- All checks passed: ☐ Yes ☐ No

### Business Verification
- Verified by: _____________________
- Date: _____________________
- All functionality working: ☐ Yes ☐ No

### Final Approval
- Approved by: _____________________
- Date: _____________________
- Ready for production: ☐ Yes ☐ No

---

## NOTES & ISSUES FOUND

```
Document any issues discovered during testing:

Issue 1:
Description: 
Severity: (Critical/High/Medium/Low)
Resolution: 
Status: (Open/In Progress/Resolved)

Issue 2:
Description: 
Severity: (Critical/High/Medium/Low)
Resolution: 
Status: (Open/In Progress/Resolved)
```

---

## ROLLBACK DECISION

After all testing:
- [ ] Continue with Supabase deployment (all tests passed)
- [ ] Rollback to Neon (issues found that need resolution)

**Decision Date**: _____________________
**Approved by**: _____________________
