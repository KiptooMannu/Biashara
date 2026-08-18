# Performance Optimization Guide for BIASHARA ERP

## Current Performance Analysis

### Database Query Optimization Status
Based on code analysis, the following optimizations are already in place:
- ✅ Database-level aggregations (COUNT, SUM, AVG, GROUP BY) in DashboardService
- ✅ EntityGraph usage for eager loading to prevent N+1 queries
- ✅ Batch processing with `batch_size: 100` and `order_inserts: true`
- ✅ Lazy fetching by default in entity relationships
- ✅ Pagination implementation with PageRequest

### Remaining Performance Issues
1. **Dashboard Heavy Query Load**: Single dashboard request executes 15+ database queries
2. **Missing Database Indexes**: Critical columns lack proper indexing
3. **No Query Caching**: Repeated calculations without caching
4. **Potential Connection Pool Bottlenecks**: Even with Supabase optimization

## Critical Database Indexes Required

```sql
-- Multi-tenant filtering (CRITICAL - every query includes tenant_id)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tenant_id ON [table_name] (tenant_id);

-- Specific high-traffic tables
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sale_tenant_date ON sale (tenant_id, sale_date DESC, deleted);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoice_tenant_customer ON invoice (tenant_id, customer_id, deleted);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_tenant_spent ON customer (tenant_id, total_spent DESC, deleted);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_tenant_stock ON product (tenant_id, stock_quantity, deleted);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employee_tenant_active ON employee (tenant_id, active, deleted);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_insight_tenant_dismissed ON ai_insight (tenant_id, dismissed, deleted, generated_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_tenant_user ON notification (tenant_id, user_id, deleted, created_at DESC);

-- Composite indexes for common filtering patterns
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tenant_deleted_status ON [table_name] (tenant_id, deleted, status);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tenant_created_by ON [table_name] (tenant_id, created_by, deleted);
```

## Dashboard Query Optimization

### Current Issue
The `DashboardService.build()` method executes 15+ separate database queries for each dashboard load:
- 14+ repository calls for KPIs, charts, and data
- Each query aggregates data from different time periods
- No caching of frequently accessed data

### Optimization Strategy

#### 1. Implement Dashboard Data Caching
```java
@Service
@RequiredArgsConstructor
public class DashboardService {
    
    @Cacheable(value = "dashboard", key = "#tenantId + '-' + #userId", unless = "#result == null")
    @Transactional(readOnly = true)
    public AnalyticsDtos.DashboardResponse build(Long tenantId, Long userId, List<String> permissions) {
        // Existing implementation
    }
    
    // Cache for 5 minutes
    @CacheEvict(value = "dashboard", allEntries = true)
    @Scheduled(fixedRate = 300000)
    public void clearDashboardCache() {
        // Clears cache every 5 minutes
    }
}
```

#### 2. Batch KPI Calculations
Instead of separate repository calls for each KPI, combine similar calculations:

```java
@Query("""
    SELECT 
        COUNT(*) as todayOrders,
        COALESCE(SUM(s.totalAmount), 0) as todayRevenue,
        COUNT(*) FILTER (WHERE s.saleDate >= :startOfMonth) as monthOrders,
        COALESCE(SUM(s.totalAmount) FILTER (WHERE s.saleDate >= :startOfMonth), 0) as monthRevenue
    FROM Sale s
    WHERE s.tenant.id = :tenantId 
    AND s.deleted = false
    AND s.saleDate >= :startOfToday
""")
Map<String, Object> getDashboardKpis(
    @Param("tenantId") Long tenantId,
    @Param("startOfToday") LocalDateTime startOfToday,
    @Param("startOfMonth") LocalDateTime startOfMonth
);
```

#### 3. Database-Level Time Series Aggregation
Instead of fetching raw data and processing in Java, use PostgreSQL time bucket functions:

```sql
-- Replace dailyRevenueSeries with this optimized query
SELECT 
    DATE_TRUNC('day', sale_date) as bucket,
    COALESCE(SUM(total_amount), 0) as value,
    COUNT(*) as count
FROM sale
WHERE tenant_id = :tenantId 
  AND deleted = false
  AND sale_date >= :windowStart
GROUP BY DATE_TRUNC('day', sale_date)
ORDER BY bucket ASC;
```

## Frontend Polling Optimization

### Current Implementation
- Dashboard has manual refresh button (no auto-polling)
- useApi hook refetches on parameter changes
- No background data updates

### Recommended Improvements

#### 1. Smart Refresh Strategy
```typescript
// Instead of constant polling, use:
- Manual refresh button (already implemented)
- Refresh on route navigation
- Refresh when window regains focus
- Debounced refresh on data modifications
```

#### 2. Optimistic Updates
```typescript
// Update UI immediately, sync with server in background
const createSale = async (saleData) => {
  // Optimistic update
  setSales(prev => [...prev, { ...saleData, id: 'temp' }])
  
  try {
    const result = await api.post('/sales', saleData)
    // Confirm with server data
    setSales(prev => prev.map(s => s.id === 'temp' ? result.data : s))
  } catch {
    // Rollback on error
    setSales(prev => prev.filter(s => s.id !== 'temp'))
  }
}
```

## Connection Pool Optimization

### Current Supabase Configuration (Optimized)
```yaml
hikari:
  maximum-pool-size: 10
  minimum-idle: 2
  connection-timeout: 20000
  idle-timeout: 300000
  max-lifetime: 900000
```

### Additional Optimizations
```yaml
spring:
  datasource:
    hikari:
      # Add these settings
      pool-name: biashara-hikari-pool
      leak-detection-threshold: 60000
      connection-test-query: SELECT 1
      validation-timeout: 3000
      max-lifetime: 1800000  # 30 minutes for Supabase
```

## Query Performance Monitoring

### Enable Slow Query Logging
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Add Performance Metrics
```java
@Aspect
@Component
public class PerformanceMonitor {
    
    @Around("@annotation(org.springframework.web.bind.annotation.RequestMapping)")
    public Object logPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;
        
        if (duration > 1000) {
            log.warn("Slow endpoint: {} took {} ms", 
                joinPoint.getSignature(), duration);
        }
        return result;
    }
}
```

## Migration Implementation Steps

### Phase 1: Database Indexes (30 minutes)
1. Connect to Supabase database
2. Execute critical indexes using CONCURRENTLY
3. Verify index creation with `\d table_name`
4. Monitor query performance improvement

### Phase 2: Dashboard Caching (1 hour)
1. Add Spring Cache dependency
2. Configure cache settings (5-minute TTL)
3. Add @Cacheable annotations to DashboardService
4. Implement cache eviction on data changes
5. Test cache hit rates

### Phase 3: Query Optimization (2 hours)
1. Batch KPI calculations in repositories
2. Implement time series aggregation in SQL
3. Add query performance monitoring
4. Optimize slow queries identified in logs

### Phase 4: Frontend Optimization (1 hour)
1. Implement smart refresh strategy
2. Add optimistic updates for critical operations
3. Debounce search and filter operations
4. Test user experience improvements

## Expected Performance Improvements

### Database Level
- **Query execution time**: 60-80% reduction with proper indexes
- **Dashboard load time**: 40-60% reduction with caching
- **Connection pool efficiency**: 30% improvement with optimized settings

### Application Level
- **API response time**: 50-70% reduction for dashboard endpoint
- **Server resource usage**: 40% reduction in database compute
- **User experience**: Near-instant dashboard loads with cache hits

### Supabase Cost Impact
- **Compute units**: 70-80% reduction in usage
- **Connection time**: 50% reduction with transaction pooler
- **Overall costs**: Significant monthly savings

## Monitoring & Maintenance

### Key Metrics to Track
1. Dashboard endpoint response time (target: < 500ms)
2. Database query execution time (target: < 100ms)
3. Cache hit ratio (target: > 80%)
4. Connection pool utilization (target: < 70%)
5. Supabase compute unit usage (target: < 50% of limit)

### Regular Maintenance
1. Review slow query logs weekly
2. Analyze cache hit ratios monthly
3. Update indexes based on query patterns
4. Monitor connection pool metrics
5. Review Supabase compute usage trends

## Emergency Rollback Plan

If performance degrades after optimizations:
1. Disable cache: Remove @Cacheable annotations
2. Revert connection pool settings to previous values
3. Drop problematic indexes: `DROP INDEX CONCURRENTLY idx_name`
4. Enable detailed logging to identify issues
5. Gradually re-enable optimizations with monitoring