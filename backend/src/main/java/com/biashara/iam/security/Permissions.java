package com.biashara.iam.security;

import java.util.List;

/**
 * The complete permission catalogue.
 *
 * Endpoints authorize against these codes, never against role names, so a business
 * can define a new role at runtime and have it work without a redeploy. The
 * {@link #catalogue()} entries are what the seeder writes into the permission
 * table and what the permission-matrix screen renders.
 */
public final class Permissions {

    private Permissions() {
    }

    // --- Dashboard & BI -----------------------------------------------------
    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String DASHBOARD_EXECUTIVE = "dashboard.executive";
    public static final String ANALYTICS_VIEW = "analytics.view";

    // --- Inventory ----------------------------------------------------------
    public static final String PRODUCT_VIEW = "inventory.product.view";
    public static final String PRODUCT_CREATE = "inventory.product.create";
    public static final String PRODUCT_UPDATE = "inventory.product.update";
    public static final String PRODUCT_DELETE = "inventory.product.delete";
    public static final String STOCK_ADJUST = "inventory.stock.adjust";
    public static final String WAREHOUSE_MANAGE = "inventory.warehouse.manage";

    // --- Sales & POS --------------------------------------------------------
    public static final String POS_OPERATE = "sales.pos.operate";
    public static final String SALE_VIEW = "sales.sale.view";
    public static final String SALE_CREATE = "sales.sale.create";
    public static final String SALE_REFUND = "sales.sale.refund";
    public static final String SALE_VOID = "sales.sale.void";

    // --- CRM ----------------------------------------------------------------
    public static final String CUSTOMER_VIEW = "crm.customer.view";
    public static final String CUSTOMER_CREATE = "crm.customer.create";
    public static final String CUSTOMER_UPDATE = "crm.customer.update";
    public static final String CUSTOMER_DELETE = "crm.customer.delete";

    // --- Procurement --------------------------------------------------------
    public static final String SUPPLIER_VIEW = "procurement.supplier.view";
    public static final String SUPPLIER_MANAGE = "procurement.supplier.manage";
    public static final String PURCHASE_VIEW = "procurement.purchase.view";
    public static final String PURCHASE_CREATE = "procurement.purchase.create";
    public static final String PURCHASE_APPROVE = "procurement.purchase.approve";
    public static final String PURCHASE_RECEIVE = "procurement.purchase.receive";

    // --- Finance ------------------------------------------------------------
    public static final String FINANCE_VIEW = "finance.view";
    public static final String EXPENSE_VIEW = "finance.expense.view";
    public static final String EXPENSE_CREATE = "finance.expense.create";
    public static final String EXPENSE_APPROVE = "finance.expense.approve";
    public static final String EXPENSE_DELETE = "finance.expense.delete";
    public static final String INVOICE_VIEW = "finance.invoice.view";
    public static final String INVOICE_CREATE = "finance.invoice.create";
    public static final String PAYMENT_RECORD = "finance.payment.record";
    public static final String ACCOUNTING_VIEW = "finance.accounting.view";
    public static final String ACCOUNTING_POST = "finance.accounting.post";

    // --- HR -----------------------------------------------------------------
    public static final String EMPLOYEE_VIEW = "hr.employee.view";
    public static final String EMPLOYEE_MANAGE = "hr.employee.manage";
    public static final String ATTENDANCE_VIEW = "hr.attendance.view";
    public static final String ATTENDANCE_MANAGE = "hr.attendance.manage";
    public static final String LEAVE_VIEW = "hr.leave.view";
    public static final String LEAVE_APPROVE = "hr.leave.approve";
    public static final String PAYROLL_VIEW = "hr.payroll.view";
    public static final String PAYROLL_PROCESS = "hr.payroll.process";

    // --- Assets & Projects --------------------------------------------------
    public static final String ASSET_VIEW = "asset.view";
    public static final String ASSET_MANAGE = "asset.manage";
    public static final String PROJECT_VIEW = "project.view";
    public static final String PROJECT_MANAGE = "project.manage";

    // --- Reports ------------------------------------------------------------
    public static final String REPORT_VIEW = "report.view";
    public static final String REPORT_EXPORT = "report.export";
    public static final String REPORT_FINANCIAL = "report.financial";

    // --- AI -----------------------------------------------------------------
    public static final String AI_INSIGHTS_VIEW = "ai.insight.view";
    public static final String AI_ASSISTANT_USE = "ai.assistant.use";

    // --- Administration -----------------------------------------------------
    public static final String USER_VIEW = "admin.user.view";
    public static final String USER_CREATE = "admin.user.create";
    public static final String USER_UPDATE = "admin.user.update";
    public static final String USER_DELETE = "admin.user.delete";
    public static final String USER_RESET_PASSWORD = "admin.user.reset_password";
    public static final String ROLE_VIEW = "admin.role.view";
    public static final String ROLE_MANAGE = "admin.role.manage";
    public static final String AUDIT_VIEW = "admin.audit.view";
    public static final String SETTINGS_VIEW = "admin.settings.view";
    public static final String SETTINGS_MANAGE = "admin.settings.manage";

    // --- Platform (cross-tenant) --------------------------------------------
    public static final String PLATFORM_TENANT_MANAGE = "platform.tenant.manage";
    public static final String PLATFORM_METRICS_VIEW = "platform.metrics.view";

    /** code, module, description — the seed definition of every permission. */
    public static List<String[]> catalogue() {
        return List.of(
                new String[]{DASHBOARD_VIEW, "Dashboard", "View the dashboard"},
                new String[]{DASHBOARD_EXECUTIVE, "Dashboard", "View the executive command centre"},
                new String[]{ANALYTICS_VIEW, "Dashboard", "View business intelligence and analytics"},

                new String[]{PRODUCT_VIEW, "Inventory", "View products and stock levels"},
                new String[]{PRODUCT_CREATE, "Inventory", "Add new products"},
                new String[]{PRODUCT_UPDATE, "Inventory", "Edit products and pricing"},
                new String[]{PRODUCT_DELETE, "Inventory", "Remove products"},
                new String[]{STOCK_ADJUST, "Inventory", "Adjust stock and record movements"},
                new String[]{WAREHOUSE_MANAGE, "Inventory", "Manage warehouses and transfers"},

                new String[]{POS_OPERATE, "Sales", "Operate the point of sale"},
                new String[]{SALE_VIEW, "Sales", "View sales and receipts"},
                new String[]{SALE_CREATE, "Sales", "Record a sale"},
                new String[]{SALE_REFUND, "Sales", "Process refunds and returns"},
                new String[]{SALE_VOID, "Sales", "Void a completed sale"},

                new String[]{CUSTOMER_VIEW, "CRM", "View customers"},
                new String[]{CUSTOMER_CREATE, "CRM", "Add customers"},
                new String[]{CUSTOMER_UPDATE, "CRM", "Edit customers"},
                new String[]{CUSTOMER_DELETE, "CRM", "Remove customers"},

                new String[]{SUPPLIER_VIEW, "Procurement", "View suppliers"},
                new String[]{SUPPLIER_MANAGE, "Procurement", "Manage suppliers and contracts"},
                new String[]{PURCHASE_VIEW, "Procurement", "View purchase orders"},
                new String[]{PURCHASE_CREATE, "Procurement", "Raise purchase orders"},
                new String[]{PURCHASE_APPROVE, "Procurement", "Approve purchase orders"},
                new String[]{PURCHASE_RECEIVE, "Procurement", "Receive deliveries into stock"},

                new String[]{FINANCE_VIEW, "Finance", "Access the finance module"},
                new String[]{EXPENSE_VIEW, "Finance", "View expenses"},
                new String[]{EXPENSE_CREATE, "Finance", "Record expenses"},
                new String[]{EXPENSE_APPROVE, "Finance", "Approve expenses"},
                new String[]{EXPENSE_DELETE, "Finance", "Delete financial records"},
                new String[]{INVOICE_VIEW, "Finance", "View invoices"},
                new String[]{INVOICE_CREATE, "Finance", "Issue invoices"},
                new String[]{PAYMENT_RECORD, "Finance", "Record payments received"},
                new String[]{ACCOUNTING_VIEW, "Finance", "View the ledger and chart of accounts"},
                new String[]{ACCOUNTING_POST, "Finance", "Post journal entries"},

                new String[]{EMPLOYEE_VIEW, "HR", "View employees"},
                new String[]{EMPLOYEE_MANAGE, "HR", "Manage employee records"},
                new String[]{ATTENDANCE_VIEW, "HR", "View attendance"},
                new String[]{ATTENDANCE_MANAGE, "HR", "Record and correct attendance"},
                new String[]{LEAVE_VIEW, "HR", "View leave requests"},
                new String[]{LEAVE_APPROVE, "HR", "Approve leave requests"},
                new String[]{PAYROLL_VIEW, "HR", "View payroll"},
                new String[]{PAYROLL_PROCESS, "HR", "Run payroll"},

                new String[]{ASSET_VIEW, "Assets", "View the asset register"},
                new String[]{ASSET_MANAGE, "Assets", "Manage assets and maintenance"},
                new String[]{PROJECT_VIEW, "Projects", "View projects and tasks"},
                new String[]{PROJECT_MANAGE, "Projects", "Manage projects and tasks"},

                new String[]{REPORT_VIEW, "Reports", "View reports"},
                new String[]{REPORT_EXPORT, "Reports", "Export reports"},
                new String[]{REPORT_FINANCIAL, "Reports", "View financial statements"},

                new String[]{AI_INSIGHTS_VIEW, "AI", "View AI insights"},
                new String[]{AI_ASSISTANT_USE, "AI", "Ask the AI business assistant"},

                new String[]{USER_VIEW, "Administration", "View users"},
                new String[]{USER_CREATE, "Administration", "Create users"},
                new String[]{USER_UPDATE, "Administration", "Edit users and assign roles"},
                new String[]{USER_DELETE, "Administration", "Deactivate or delete users"},
                new String[]{USER_RESET_PASSWORD, "Administration", "Reset user passwords"},
                new String[]{ROLE_VIEW, "Administration", "View roles and permissions"},
                new String[]{ROLE_MANAGE, "Administration", "Create and edit roles"},
                new String[]{AUDIT_VIEW, "Administration", "View audit and login history"},
                new String[]{SETTINGS_VIEW, "Administration", "View business settings"},
                new String[]{SETTINGS_MANAGE, "Administration", "Change business settings"},

                new String[]{PLATFORM_TENANT_MANAGE, "Platform", "Create and manage tenant businesses"},
                new String[]{PLATFORM_METRICS_VIEW, "Platform", "View platform-wide metrics"});
    }
}
