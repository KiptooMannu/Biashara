package com.biashara.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Creates performance indexes for Supabase database on application startup.
 * These indexes optimize query performance and prevent compute limit issues.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class IndexCreator implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        log.info("Starting performance index creation for Supabase...");
        
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        
        // Create performance indexes
        createIndexIfNotExists(jdbcTemplate, "idx_sale_tenant_date_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sale_tenant_date_deleted ON sale (tenant_id, sale_date DESC, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_sale_tenant_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sale_tenant_deleted ON sale (tenant_id, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_sale_customer_tenant", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sale_customer_tenant ON sale (customer_id, tenant_id, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_invoice_tenant_status_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoice_tenant_status_deleted ON invoice (tenant_id, status, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_invoice_customer_tenant", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_invoice_customer_tenant ON invoice (customer_id, tenant_id, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_customer_tenant_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_tenant_deleted ON customer (tenant_id, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_customer_tenant_spent_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_customer_tenant_spent_deleted ON customer (tenant_id, total_spent DESC, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_product_tenant_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_tenant_deleted ON product (tenant_id, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_product_tenant_stock_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_product_tenant_stock_deleted ON product (tenant_id, stock_quantity, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_employee_tenant_active_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_employee_tenant_active_deleted ON employee (tenant_id, active, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_expense_tenant_date_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_expense_tenant_date_deleted ON expense (tenant_id, expense_date DESC, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_insight_tenant_dismissed_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_insight_tenant_dismissed_deleted ON ai_insight (tenant_id, dismissed, deleted, generated_at DESC)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_notification_tenant_user_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_tenant_user_deleted ON notification (tenant_id, user_id, deleted, created_at DESC)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_purchase_tenant_status_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_purchase_tenant_status_deleted ON purchase (tenant_id, status, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_inventory_tenant_product_date", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory_tenant_product_date ON inventory_transaction (tenant_id, product_id, transaction_date DESC, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_user_tenant_email_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_tenant_email_deleted ON \"user\" (tenant_id, lower(email), deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_user_tenant_status_deleted", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_tenant_status_deleted ON \"user\" (tenant_id, status, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_role_tenant_hierarchy", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_role_tenant_hierarchy ON role (tenant_id, hierarchy_level, deleted)");
            
        createIndexIfNotExists(jdbcTemplate, "idx_project_task_tenant_status_position", 
            "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_project_task_tenant_status_position ON project_task (tenant_id, status, board_position, deleted)");
        
        log.info("Performance index creation completed successfully");
    }
    
    private void createIndexIfNotExists(JdbcTemplate jdbcTemplate, String indexName, String sql) {
        try {
            // Check if index already exists
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?", 
                Integer.class, 
                indexName
            );
            
            if (count != null && count > 0) {
                log.info("Index {} already exists, skipping", indexName);
                return;
            }
            
            // Create the index (remove CONCURRENTLY for pooler compatibility)
            String nonConcurrentSql = sql.replace("CREATE INDEX CONCURRENTLY IF NOT EXISTS", "CREATE INDEX IF NOT EXISTS");
            jdbcTemplate.execute(nonConcurrentSql);
            log.info("Created index: {}", indexName);
            
        } catch (Exception e) {
            log.warn("Could not create index {}: {} (this is expected with transaction pooler - indexes should be created via direct connection)", indexName, e.getMessage());
            // Continue with other indexes even if one fails
        }
    }
}