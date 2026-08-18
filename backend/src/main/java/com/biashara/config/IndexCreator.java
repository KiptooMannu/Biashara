package com.biashara.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Creates performance indexes for Supabase database on application startup.
 * Currently disabled to prevent startup issues - indexes should be created manually.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class IndexCreator implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        // Skip automatic index creation to prevent startup issues
        // Indexes should be created manually via Supabase SQL Editor
        log.info("Skipping automatic index creation - please run the index creation script manually in Supabase SQL Editor");
        log.info("Script location: backend/src/main/resources/db/migration/V1__add_performance_indexes_simple.sql");
        log.info("This prevents startup delays and memory issues during deployment");
    }
}