package com.biashara.seed;

import com.biashara.iam.security.Permissions;

import java.util.List;
import java.util.Set;

/**
 * The role catalogue and the permission matrix behind it.
 *
 * Hierarchy levels drive who may create whom: a user can only create, edit or
 * assign roles at a strictly higher (less privileged) level than their own. That
 * is what makes the specification's rule — "managers can only create users within
 * their own department" — enforceable rather than aspirational.
 */
public final class SeedRoles {

    private SeedRoles() {
    }

    public static final int LEVEL_PLATFORM = 0;
    public static final int LEVEL_OWNER = 10;
    public static final int LEVEL_ADMIN = 20;
    public static final int LEVEL_GENERAL_MANAGER = 30;
    public static final int LEVEL_DEPARTMENT_MANAGER = 40;
    public static final int LEVEL_STAFF = 50;

    /** A role definition: code, display name, level, owning department, permissions. */
    public record RoleSeed(
            String code,
            String name,
            String description,
            int level,
            String departmentCode,
            Set<String> permissions) {
    }

    // Bundles reused across roles, so the matrix stays readable.

    private static final Set<String> SELF_SERVICE = Set.of(
            Permissions.DASHBOARD_VIEW);

    private static final Set<String> INVENTORY_FULL = Set.of(
            Permissions.PRODUCT_VIEW, Permissions.PRODUCT_CREATE, Permissions.PRODUCT_UPDATE,
            Permissions.PRODUCT_DELETE, Permissions.STOCK_ADJUST, Permissions.WAREHOUSE_MANAGE);

    private static final Set<String> SALES_FULL = Set.of(
            Permissions.POS_OPERATE, Permissions.SALE_VIEW, Permissions.SALE_CREATE,
            Permissions.SALE_REFUND, Permissions.SALE_VOID);

    private static final Set<String> CRM_FULL = Set.of(
            Permissions.CUSTOMER_VIEW, Permissions.CUSTOMER_CREATE,
            Permissions.CUSTOMER_UPDATE, Permissions.CUSTOMER_DELETE);

    private static final Set<String> PROCUREMENT_FULL = Set.of(
            Permissions.SUPPLIER_VIEW, Permissions.SUPPLIER_MANAGE, Permissions.PURCHASE_VIEW,
            Permissions.PURCHASE_CREATE, Permissions.PURCHASE_APPROVE, Permissions.PURCHASE_RECEIVE);

    private static final Set<String> FINANCE_FULL = Set.of(
            Permissions.FINANCE_VIEW, Permissions.EXPENSE_VIEW, Permissions.EXPENSE_CREATE,
            Permissions.EXPENSE_APPROVE, Permissions.EXPENSE_DELETE, Permissions.INVOICE_VIEW,
            Permissions.INVOICE_CREATE, Permissions.PAYMENT_RECORD, Permissions.ACCOUNTING_VIEW,
            Permissions.ACCOUNTING_POST, Permissions.REPORT_FINANCIAL);

    private static final Set<String> HR_FULL = Set.of(
            Permissions.EMPLOYEE_VIEW, Permissions.EMPLOYEE_MANAGE, Permissions.ATTENDANCE_VIEW,
            Permissions.ATTENDANCE_MANAGE, Permissions.LEAVE_VIEW, Permissions.LEAVE_APPROVE,
            Permissions.PAYROLL_VIEW, Permissions.PAYROLL_PROCESS);

    private static final Set<String> REPORTS_BASIC = Set.of(
            Permissions.REPORT_VIEW, Permissions.REPORT_EXPORT);

    private static final Set<String> AI_FULL = Set.of(
            Permissions.AI_INSIGHTS_VIEW, Permissions.AI_ASSISTANT_USE);

    private static final Set<String> USER_ADMIN = Set.of(
            Permissions.USER_VIEW, Permissions.USER_CREATE, Permissions.USER_UPDATE,
            Permissions.USER_DELETE, Permissions.USER_RESET_PASSWORD,
            Permissions.ROLE_VIEW, Permissions.ROLE_MANAGE, Permissions.AUDIT_VIEW);

    @SafeVarargs
    private static Set<String> union(Set<String>... groups) {
        java.util.Set<String> combined = new java.util.HashSet<>();
        for (Set<String> group : groups) {
            combined.addAll(group);
        }
        return Set.copyOf(combined);
    }

    /** Everything in the catalogue — used for the owner and the platform admin. */
    private static Set<String> everything() {
        return Permissions.catalogue().stream()
                .map(entry -> entry[0])
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static List<RoleSeed> all() {
        return List.of(
                new RoleSeed("SUPER_ADMIN", "Platform Super Admin",
                        "Manages every business on the platform, subscription plans and tenant provisioning.",
                        LEVEL_PLATFORM, null, everything()),

                new RoleSeed("BUSINESS_OWNER", "Business Owner",
                        "Complete control of this business, including users, finance and settings.",
                        LEVEL_OWNER, null,
                        // Everything except cross-tenant platform administration.
                        Set.copyOf(everything().stream()
                                .filter(code -> !code.startsWith("platform."))
                                .toList())),

                new RoleSeed("BUSINESS_ADMIN", "Business Administrator",
                        "Manages users, roles and settings. No access to financial approvals.",
                        LEVEL_ADMIN, null,
                        union(SELF_SERVICE, USER_ADMIN, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.SETTINGS_VIEW, Permissions.SETTINGS_MANAGE,
                                        Permissions.PRODUCT_VIEW, Permissions.CUSTOMER_VIEW,
                                        Permissions.EMPLOYEE_VIEW))),

                new RoleSeed("GENERAL_MANAGER", "General Manager",
                        "Oversees all departments and day-to-day operations. Cannot delete financial records or manage settings.",
                        LEVEL_GENERAL_MANAGER, null,
                        union(SELF_SERVICE, INVENTORY_FULL, CRM_FULL, PROCUREMENT_FULL, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.ANALYTICS_VIEW, Permissions.SALE_VIEW,
                                        Permissions.EMPLOYEE_VIEW, Permissions.ATTENDANCE_VIEW,
                                        Permissions.LEAVE_VIEW, Permissions.LEAVE_APPROVE,
                                        Permissions.ASSET_VIEW, Permissions.PROJECT_VIEW,
                                        Permissions.PROJECT_MANAGE, Permissions.USER_VIEW))),

                new RoleSeed("FINANCE_MANAGER", "Finance Manager",
                        "Owns finance, expenses, invoicing and the ledger. No access to HR records.",
                        LEVEL_DEPARTMENT_MANAGER, "FIN",
                        union(SELF_SERVICE, FINANCE_FULL, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.ANALYTICS_VIEW, Permissions.SALE_VIEW,
                                        Permissions.CUSTOMER_VIEW, Permissions.PURCHASE_VIEW,
                                        Permissions.ASSET_VIEW, Permissions.USER_VIEW))),

                new RoleSeed("HR_MANAGER", "HR Manager",
                        "Owns employees, attendance, leave and payroll. No access to finance.",
                        LEVEL_DEPARTMENT_MANAGER, "HR",
                        union(SELF_SERVICE, HR_FULL, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.USER_VIEW, Permissions.USER_CREATE,
                                        Permissions.USER_UPDATE, Permissions.ANALYTICS_VIEW))),

                new RoleSeed("INVENTORY_MANAGER", "Inventory Manager",
                        "Owns products, stock, warehouses and transfers.",
                        LEVEL_DEPARTMENT_MANAGER, "OPS",
                        union(SELF_SERVICE, INVENTORY_FULL, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.SUPPLIER_VIEW, Permissions.PURCHASE_VIEW,
                                        Permissions.PURCHASE_CREATE, Permissions.PURCHASE_RECEIVE,
                                        Permissions.ANALYTICS_VIEW, Permissions.USER_VIEW,
                                        Permissions.USER_CREATE))),

                new RoleSeed("PROCUREMENT_MANAGER", "Procurement Manager",
                        "Owns suppliers, purchase orders, approvals and supplier scorecards.",
                        LEVEL_DEPARTMENT_MANAGER, "OPS",
                        union(SELF_SERVICE, PROCUREMENT_FULL, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.PRODUCT_VIEW, Permissions.STOCK_ADJUST,
                                        Permissions.ANALYTICS_VIEW, Permissions.USER_VIEW))),

                new RoleSeed("SALES_MANAGER", "Sales Manager",
                        "Owns the POS, sales team, customers and sales reporting.",
                        LEVEL_DEPARTMENT_MANAGER, "SLS",
                        union(SELF_SERVICE, SALES_FULL, CRM_FULL, REPORTS_BASIC, AI_FULL,
                                Set.of(Permissions.PRODUCT_VIEW, Permissions.ANALYTICS_VIEW,
                                        Permissions.INVOICE_VIEW, Permissions.INVOICE_CREATE,
                                        Permissions.PAYMENT_RECORD, Permissions.USER_VIEW,
                                        Permissions.USER_CREATE))),

                new RoleSeed("ACCOUNTANT", "Accountant",
                        "Records expenses, invoices and payments. Cannot approve or delete.",
                        LEVEL_STAFF, "FIN",
                        union(SELF_SERVICE,
                                Set.of(Permissions.FINANCE_VIEW, Permissions.EXPENSE_VIEW,
                                        Permissions.EXPENSE_CREATE, Permissions.INVOICE_VIEW,
                                        Permissions.INVOICE_CREATE, Permissions.PAYMENT_RECORD,
                                        Permissions.ACCOUNTING_VIEW, Permissions.REPORT_VIEW,
                                        Permissions.CUSTOMER_VIEW))),

                new RoleSeed("CASHIER", "POS Cashier",
                        "Operates the till, serves customers and prints receipts. No finance, HR or analytics access.",
                        LEVEL_STAFF, "SLS",
                        union(SELF_SERVICE,
                                Set.of(Permissions.POS_OPERATE, Permissions.SALE_VIEW,
                                        Permissions.SALE_CREATE, Permissions.CUSTOMER_VIEW,
                                        Permissions.CUSTOMER_CREATE, Permissions.PRODUCT_VIEW))),

                new RoleSeed("STOREKEEPER", "Storekeeper",
                        "Receives deliveries and records stock movements.",
                        LEVEL_STAFF, "OPS",
                        union(SELF_SERVICE,
                                Set.of(Permissions.PRODUCT_VIEW, Permissions.STOCK_ADJUST,
                                        Permissions.PURCHASE_VIEW, Permissions.PURCHASE_RECEIVE,
                                        Permissions.SUPPLIER_VIEW))),

                new RoleSeed("HR_OFFICER", "HR Officer",
                        "Records attendance and processes leave requests.",
                        LEVEL_STAFF, "HR",
                        union(SELF_SERVICE,
                                Set.of(Permissions.EMPLOYEE_VIEW, Permissions.ATTENDANCE_VIEW,
                                        Permissions.ATTENDANCE_MANAGE, Permissions.LEAVE_VIEW))),

                new RoleSeed("SALES_REP", "Sales Representative",
                        "Serves customers and logs interactions in the field.",
                        LEVEL_STAFF, "SLS",
                        union(SELF_SERVICE,
                                Set.of(Permissions.POS_OPERATE, Permissions.SALE_VIEW,
                                        Permissions.SALE_CREATE, Permissions.CUSTOMER_VIEW,
                                        Permissions.CUSTOMER_CREATE, Permissions.CUSTOMER_UPDATE,
                                        Permissions.PRODUCT_VIEW))));
    }
}
