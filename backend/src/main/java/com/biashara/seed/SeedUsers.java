package com.biashara.seed;

import java.util.List;

/**
 * The demo logins.
 *
 * Every role in the hierarchy gets an account so that role-based access control
 * can actually be exercised rather than described. The three headline accounts
 * (owner, manager, cashier) skip the forced password change so a reviewer lands
 * straight on a populated dashboard; {@code newuser} deliberately does not, so the
 * invitation and first-login flow is demonstrable too.
 */
public final class SeedUsers {

    private SeedUsers() {
    }

    public record UserSeed(
            String email,
            String password,
            String firstName,
            String lastName,
            String roleCode,
            String position,
            String departmentCode,
            String phone,
            String description,
            /** True for the account used to demonstrate the first-login flow. */
            boolean forcePasswordChange,
            /** Shown prominently on the sign-in screen. */
            boolean headline) {
    }

    public static List<UserSeed> all() {
        return List.of(
                new UserSeed("owner@biashara.demo", "Owner@123", "James", "Kariuki",
                        "BUSINESS_OWNER", "Managing Director", null, "+254 722 100 001",
                        "Full access: every module, business intelligence, users and settings.",
                        false, true),

                new UserSeed("manager@biashara.demo", "Manager@123", "Grace", "Wanjiru",
                        "GENERAL_MANAGER", "General Manager", null, "+254 722 100 002",
                        "Operations oversight: inventory, CRM, suppliers, purchases, employees and reports. "
                                + "Cannot delete financial data, change settings or manage users.",
                        false, true),

                new UserSeed("cashier@biashara.demo", "Cashier@123", "Brian", "Otieno",
                        "CASHIER", "Till Operator", "SLS", "+254 722 100 003",
                        "Point of sale only: sell, serve customers, print receipts. "
                                + "No finance, HR, analytics or product editing.",
                        false, true),

                new UserSeed("admin@biashara.demo", "Admin@123", "Sarah", "Chebet",
                        "BUSINESS_ADMIN", "Business Administrator", null, "+254 722 100 004",
                        "User and role administration, settings and the audit trail.",
                        false, false),

                new UserSeed("finance@biashara.demo", "Finance@123", "Daniel", "Mutiso",
                        "FINANCE_MANAGER", "Finance Manager", "FIN", "+254 722 100 005",
                        "Finance, expenses, invoicing, the ledger and financial statements. No HR access.",
                        false, false),

                new UserSeed("hr@biashara.demo", "HrDemo@123", "Faith", "Njoki",
                        "HR_MANAGER", "HR Manager", "HR", "+254 722 100 006",
                        "Employees, attendance, leave and payroll. No finance access.",
                        false, false),

                new UserSeed("inventory@biashara.demo", "Stock@123", "Peter", "Kiptoo",
                        "INVENTORY_MANAGER", "Inventory Manager", "OPS", "+254 722 100 007",
                        "Products, stock levels, warehouses and stock movements.",
                        false, false),

                new UserSeed("procurement@biashara.demo", "Procure@123", "Alice", "Nduta",
                        "PROCUREMENT_MANAGER", "Procurement Manager", "OPS", "+254 722 100 008",
                        "Suppliers, purchase orders, approvals and supplier scorecards.",
                        false, false),

                new UserSeed("sales@biashara.demo", "Sales@123", "Kevin", "Omondi",
                        "SALES_MANAGER", "Sales Manager", "SLS", "+254 722 100 009",
                        "Sales team, POS oversight, customers and sales reporting.",
                        false, false),

                new UserSeed("accountant@biashara.demo", "Accounts@123", "Mary", "Achieng",
                        "ACCOUNTANT", "Accountant", "FIN", "+254 722 100 010",
                        "Records expenses, invoices and payments. Cannot approve or delete.",
                        false, false),

                new UserSeed("store@biashara.demo", "Store@123", "Samuel", "Barasa",
                        "STOREKEEPER", "Storekeeper", "OPS", "+254 722 100 011",
                        "Receives deliveries and records stock movements.",
                        false, false),

                new UserSeed("superadmin@biashara.demo", "Super@123", "Platform", "Administrator",
                        "SUPER_ADMIN", "Platform Super Admin", null, "+254 722 100 012",
                        "Cross-tenant platform administration and subscription management.",
                        false, false),

                new UserSeed("newuser@biashara.demo", "Temp@123", "Cynthia", "Wafula",
                        "HR_OFFICER", "HR Officer", "HR", "+254 722 100 013",
                        "Newly invited account. Signing in forces a password change first — "
                                + "this is the invitation flow, not a shortcut around it.",
                        true, false));
    }
}
