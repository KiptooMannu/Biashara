package com.biashara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BIASHARA - AI-powered multi-tenant ERP for MSMEs.
 *
 * Deployed as a modular monolith: each top-level package under com.biashara is a
 * bounded context (auth, iam, inventory, sales, crm, procurement, finance, hr,
 * analytics, ai, notification) that maps 1:1 onto the service boundaries in the
 * target microservice architecture. Splitting later means extracting a package,
 * not untangling a codebase.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class BiasharaApplication {

    public static void main(String[] args) {
        SpringApplication.run(BiasharaApplication.class, args);
    }
}
