/** Response shapes from the BIASHARA API. */

export interface UserSummary {
  id: number
  email: string
  fullName: string
  initials: string
  position?: string
  avatarUrl?: string
  tenantId?: number
  tenantName?: string
  currency: string
  department?: string
  branch?: string
  roles: string[]
  primaryRoleName?: string
  hierarchyLevel: number
  platformAdmin: boolean
  permissions: string[]
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  mustChangePassword: boolean
  user: UserSummary
}

export interface DemoAccount {
  email: string
  password: string
  roleCode: string
  roleName: string
  fullName: string
  position: string
  description: string
  permissionCount: number
  canAccess: string[]
}

export interface SeriesPoint {
  bucket: string
  value: number
  secondary: number
  count: number
}

export interface LabelledValue {
  label: string
  value: number
  count: number
}

export interface KpiTile {
  key: string
  label: string
  value: number
  unit: string
  changePercent?: number
  /** Whether a rise in this figure is good news; drives the colour of the delta. */
  higherIsBetter: boolean
  hint?: string
}

export interface DashboardResponse {
  businessName: string
  currency: string
  generatedAt: string
  kpis: KpiTile[]
  revenueSeries: SeriesPoint[]
  revenueByCategory: LabelledValue[]
  revenueByPaymentMethod: LabelledValue[]
  revenueByBranch: LabelledValue[]
  topProducts: LabelledValue[]
  topCustomers: LabelledValue[]
  expenseBreakdown: LabelledValue[]
  customerGrowth: SeriesPoint[]
  inventoryMovement: SeriesPoint[]
  salesByHour: LabelledValue[]
  health: BusinessHealth
  insights: AiInsight[]
  notifications: NotificationItem[]
  lowStock: ProductRow[]
  recentSales: SaleRow[]
}

export interface BusinessHealth {
  score: number
  grade: string
  components: HealthComponent[]
}

export interface HealthComponent {
  name: string
  score: number
  weight: number
  detail: string
}

export interface AiInsight {
  id: number
  type: string
  severity: 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL'
  title: string
  summary?: string
  cause?: string
  recommendation?: string
  metricLabel?: string
  metricValue?: number
  metricUnit?: string
  changePercent?: number
  confidence?: number
  module?: string
  actionUrl?: string
  actionLabel?: string
  entityType?: string
  entityId?: number
  entityName?: string
  read: boolean
  generatedAt: string
}

export interface NotificationItem {
  id: number
  title: string
  message: string
  severity: 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL'
  module?: string
  actionUrl?: string
  read: boolean
  createdOn: string
}

export interface ProductRow {
  id: number
  sku: string
  barcode?: string
  name: string
  category?: string
  supplier?: string
  unit?: string
  buyingPrice: number
  sellingPrice: number
  vatRate?: number
  marginPercent: number
  currentStock: number
  minStock: number
  reorderLevel?: number
  stockValue: number
  salesVelocity?: number
  daysUntilStockout?: number
  expiryDate?: string
  lowStock: boolean
  outOfStock: boolean
  active: boolean
}

export interface SaleRow {
  id: number
  invoiceNumber: string
  customerName?: string
  cashierName?: string
  branchName?: string
  saleDate: string
  itemCount: number
  subtotal: number
  taxAmount: number
  discountAmount: number
  total: number
  grossProfit: number
  paymentMethod?: string
  paymentStatus?: string
  status: string
  channel?: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface ApiErrorBody {
  success: boolean
  status: number
  message: string
  fieldErrors?: Record<string, string>
}
