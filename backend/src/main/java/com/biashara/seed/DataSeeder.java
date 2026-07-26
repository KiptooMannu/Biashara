package com.biashara.seed;

import com.biashara.iam.domain.Tenant;
import com.biashara.iam.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Populates the demo business on startup.
 *
 * Runs in the order the specification lays out, because each stage depends on the
 * last: products need categories and suppliers, sales need products and customers,
 * insights need sales to reason about. The whole thing is idempotent — it keys off
 * the tenant slug and returns immediately if the business already exists, so
 * restarting the application does not duplicate data or slow the boot down.
 *
 * Disable with {@code biashara.seed.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "biashara.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String TENANT_SLUG = "greenmart";

    /** Wipe and rebuild: {@code --biashara.seed.reset=true}. */
    @org.springframework.beans.factory.annotation.Value("${biashara.seed.reset:false}")
    private boolean resetRequested;

    private final TenantRepository tenantRepository;
    private final SeedResetService seedResetService;
    private final IamSeeder iamSeeder;
    private final CatalogueSeeder catalogueSeeder;
    private final PartnerSeeder partnerSeeder;
    private final OperationsSeeder operationsSeeder;
    private final IntelligenceSeeder intelligenceSeeder;
    private final SeedReporter seedReporter;

    @Override
    public void run(ApplicationArguments args) {
        if (resetRequested) {
            seedResetService.reset();
        }

        if (tenantRepository.existsBySlug(TENANT_SLUG)) {
            log.info("Demo business '{}' already present — skipping seed.", TENANT_SLUG);
            seedReporter.logCounts();
            return;
        }

        Instant started = Instant.now();
        log.info("Seeding BIASHARA demo data. This runs once.");

        // 1-3: roles, permissions, the business, its structure and its users.
        IamSeeder.IamContext iam = iamSeeder.seed(TENANT_SLUG);
        Tenant tenant = iam.tenant();

        // 4-6: what the business sells and who supplies it.
        CatalogueSeeder.CatalogueContext catalogue = catalogueSeeder.seed(tenant);

        // 7-8: customers and staff.
        PartnerSeeder.PartnerContext partners = partnerSeeder.seed(tenant, iam);

        // 9-15: ninety days of trading — the data every chart is drawn from.
        operationsSeeder.seed(tenant, iam, catalogue, partners);

        // 16-18: ledger, assets, projects, notifications and AI insights, which are
        // derived from the transactions seeded above rather than invented.
        intelligenceSeeder.seed(tenant, iam, catalogue, partners);

        log.info("Seed complete in {} seconds.", Duration.between(started, Instant.now()).toSeconds());
        seedReporter.logCounts();
        seedReporter.logCredentials();
    }
}
