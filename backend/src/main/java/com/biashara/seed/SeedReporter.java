package com.biashara.seed;

import com.biashara.ai.repository.AiInsightRepository;
import com.biashara.asset.repository.AssetRepository;
import com.biashara.crm.repository.CustomerInteractionRepository;
import com.biashara.crm.repository.CustomerRepository;
import com.biashara.finance.repository.ExpenseRepository;
import com.biashara.finance.repository.InvoiceRepository;
import com.biashara.finance.repository.JournalEntryRepository;
import com.biashara.finance.repository.PaymentRepository;
import com.biashara.hr.repository.AttendanceRepository;
import com.biashara.hr.repository.EmployeeRepository;
import com.biashara.hr.repository.LeaveRequestRepository;
import com.biashara.hr.repository.PayrollRepository;
import com.biashara.iam.repository.BranchRepository;
import com.biashara.iam.repository.DepartmentRepository;
import com.biashara.iam.repository.PermissionRepository;
import com.biashara.iam.repository.RoleRepository;
import com.biashara.iam.repository.TenantRepository;
import com.biashara.iam.repository.UserRepository;
import com.biashara.inventory.repository.CategoryRepository;
import com.biashara.inventory.repository.InventoryTransactionRepository;
import com.biashara.inventory.repository.ProductRepository;
import com.biashara.notification.repository.NotificationRepository;
import com.biashara.procurement.repository.PurchaseRepository;
import com.biashara.procurement.repository.SupplierRepository;
import com.biashara.project.repository.ProjectRepository;
import com.biashara.project.repository.ProjectTaskRepository;
import com.biashara.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Reports what the seed actually produced.
 *
 * Printed on every boot so the demo data is verifiable rather than assumed: any
 * table that came out under the ten-row floor is flagged in the log instead of
 * being discovered as an empty screen during a walkthrough.
 */
@Component
@RequiredArgsConstructor
public class SeedReporter {

    private static final Logger log = LoggerFactory.getLogger(SeedReporter.class);
    private static final long MINIMUM_ROWS = 10;

    private final TenantRepository tenantRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final CategoryRepository categoryRepository;
    private final com.biashara.inventory.repository.WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final CustomerInteractionRepository interactionRepository;
    private final EmployeeRepository employeeRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollRepository payrollRepository;
    private final AssetRepository assetRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTaskRepository projectTaskRepository;
    private final NotificationRepository notificationRepository;
    private final AiInsightRepository insightRepository;

    @Transactional(readOnly = true)
    public void logCounts() {
        Long tenantId = tenantRepository.findBySlug(DataSeeder.TENANT_SLUG)
                .map(tenant -> tenant.getId())
                .orElse(null);
        if (tenantId == null) {
            log.warn("Demo tenant not found — nothing to report.");
            return;
        }

        Map<String, Supplier<Long>> counts = new LinkedHashMap<>();
        counts.put("Permissions", permissionRepository::count);
        counts.put("Roles", roleRepository::count);
        counts.put("Users", () -> userRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Departments", () -> departmentRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Branches", () -> branchRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Warehouses", () -> (long) warehouseRepository
                .findByTenantIdAndDeletedFalseOrderByNameAsc(tenantId).size());
        counts.put("Categories", () -> categoryRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Products", () -> productRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Suppliers", () -> supplierRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Customers", () -> customerRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Customer interactions", () -> interactionRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Employees", () -> employeeRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Sales", () -> saleRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Purchase orders", () -> purchaseRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Inventory movements",
                () -> inventoryTransactionRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Expenses", () -> expenseRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Invoices", () -> invoiceRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Payments", () -> paymentRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Journal entries", () -> journalEntryRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Attendance records", () -> attendanceRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Leave requests", () -> leaveRequestRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Payroll records", () -> payrollRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Assets", () -> assetRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Projects", () -> projectRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Project tasks", () -> projectTaskRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("Notifications", () -> notificationRepository.countByTenantIdAndDeletedFalse(tenantId));
        counts.put("AI insights", () -> insightRepository.countByTenantIdAndDeletedFalse(tenantId));

        log.info("");
        log.info("======================= BIASHARA seeded data =======================");

        int thin = 0;
        for (Map.Entry<String, Supplier<Long>> entry : counts.entrySet()) {
            long value = entry.getValue().get();
            boolean below = value < MINIMUM_ROWS;
            if (below) {
                thin++;
            }
            log.info(String.format("  %-24s %6d %s", entry.getKey(), value, below ? "<-- BELOW MINIMUM" : ""));
        }

        log.info("-------------------------------------------------------------------");
        if (thin == 0) {
            log.info("  All {} tables have at least {} rows.", counts.size(), MINIMUM_ROWS);
        } else {
            log.warn("  {} table(s) are below the {}-row minimum.", thin, MINIMUM_ROWS);
        }
        log.info("===================================================================");
    }

    public void logCredentials() {
        log.info("");
        log.info("========================== Demo logins ============================");
        for (SeedUsers.UserSeed seed : SeedUsers.all()) {
            log.info(String.format("  %-30s %-14s %s",
                    seed.email(),
                    seed.password(),
                    seed.roleCode() + (seed.forcePasswordChange() ? "  (forces password change)" : "")));
        }
        log.info("-------------------------------------------------------------------");
        log.info("  Swagger UI:  http://localhost:8080/swagger-ui.html");
        log.info("  Frontend:    http://localhost:5173");
        log.info("===================================================================");
        log.info("");
    }
}
