package com.biashara.seed;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Empties every application table.
 *
 * Exists because a seed run that fails halfway leaves the tenant row behind, and
 * the seeder then treats the database as already populated and skips. Also gives
 * the demo a way back to a known-good state: run with
 * {@code --biashara.seed.reset=true} and the data is rebuilt from scratch.
 */
@Service
@RequiredArgsConstructor
public class SeedResetService {

    private static final Logger log = LoggerFactory.getLogger(SeedResetService.class);

    /**
     * Child tables first is not strictly necessary with CASCADE, but keeping the
     * order explicit documents the dependency graph.
     */
    private static final List<String> TABLES = List.of(
            "ai_chat_messages",
            "ai_insights",
            "notifications",
            "project_tasks",
            "projects",
            "assets",
            "payroll",
            "leave_requests",
            "attendance",
            "journal_lines",
            "journal_entries",
            "payments",
            "invoices",
            "expenses",
            "accounts",
            "inventory_transactions",
            "sale_items",
            "sales",
            "purchase_items",
            "purchases",
            "customer_interactions",
            "customers",
            "products",
            "categories",
            "warehouses",
            "suppliers",
            "employees",
            "audit_logs",
            "login_history",
            "refresh_tokens",
            "password_reset_tokens",
            "user_invitations",
            "user_direct_permissions",
            "user_roles",
            "users",
            "role_permissions",
            "roles",
            "permissions",
            "departments",
            "branches",
            "tenants");

    private final EntityManager entityManager;

    @Transactional
    public void reset() {
        log.warn("Resetting all BIASHARA data — {} tables will be emptied.", TABLES.size());

        // One statement so Postgres can take all the locks at once; RESTART IDENTITY
        // puts sequences back to 1 so seeded ids are stable across resets.
        String statement = "TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE";
        entityManager.createNativeQuery(statement).executeUpdate();

        log.warn("Reset complete.");
    }
}
