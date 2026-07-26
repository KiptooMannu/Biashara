/**
 * Permission codes, mirroring the backend catalogue.
 *
 * The frontend uses these only to decide what to show. Every one of them is
 * enforced again server-side, so hiding a menu item is a convenience, not the
 * security boundary.
 */
export const P = {
  dashboardView: 'dashboard.view',
  dashboardExecutive: 'dashboard.executive',
  analyticsView: 'analytics.view',

  productView: 'inventory.product.view',
  productCreate: 'inventory.product.create',
  productUpdate: 'inventory.product.update',
  productDelete: 'inventory.product.delete',
  stockAdjust: 'inventory.stock.adjust',
  warehouseManage: 'inventory.warehouse.manage',

  posOperate: 'sales.pos.operate',
  saleView: 'sales.sale.view',
  saleCreate: 'sales.sale.create',
  saleRefund: 'sales.sale.refund',
  saleVoid: 'sales.sale.void',

  customerView: 'crm.customer.view',
  customerCreate: 'crm.customer.create',
  customerUpdate: 'crm.customer.update',
  customerDelete: 'crm.customer.delete',

  supplierView: 'procurement.supplier.view',
  supplierManage: 'procurement.supplier.manage',
  purchaseView: 'procurement.purchase.view',
  purchaseCreate: 'procurement.purchase.create',
  purchaseApprove: 'procurement.purchase.approve',
  purchaseReceive: 'procurement.purchase.receive',

  financeView: 'finance.view',
  expenseView: 'finance.expense.view',
  expenseCreate: 'finance.expense.create',
  expenseApprove: 'finance.expense.approve',
  expenseDelete: 'finance.expense.delete',
  invoiceView: 'finance.invoice.view',
  invoiceCreate: 'finance.invoice.create',
  paymentRecord: 'finance.payment.record',
  accountingView: 'finance.accounting.view',
  accountingPost: 'finance.accounting.post',

  employeeView: 'hr.employee.view',
  employeeManage: 'hr.employee.manage',
  attendanceView: 'hr.attendance.view',
  attendanceManage: 'hr.attendance.manage',
  leaveView: 'hr.leave.view',
  leaveApprove: 'hr.leave.approve',
  payrollView: 'hr.payroll.view',
  payrollProcess: 'hr.payroll.process',

  assetView: 'asset.view',
  assetManage: 'asset.manage',
  projectView: 'project.view',
  projectManage: 'project.manage',

  reportView: 'report.view',
  reportExport: 'report.export',
  reportFinancial: 'report.financial',

  aiInsightsView: 'ai.insight.view',
  aiAssistantUse: 'ai.assistant.use',

  userView: 'admin.user.view',
  userCreate: 'admin.user.create',
  userUpdate: 'admin.user.update',
  userDelete: 'admin.user.delete',
  userResetPassword: 'admin.user.reset_password',
  roleView: 'admin.role.view',
  roleManage: 'admin.role.manage',
  auditView: 'admin.audit.view',
  settingsView: 'admin.settings.view',
  settingsManage: 'admin.settings.manage',

  platformTenantManage: 'platform.tenant.manage',
  platformMetricsView: 'platform.metrics.view',
} as const

export type PermissionCode = (typeof P)[keyof typeof P]
