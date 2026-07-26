import {
  Banknote,
  BarChart3,
  Boxes,
  Building2,
  ClipboardList,
  Contact,
  FileText,
  Gauge,
  HardDrive,
  LayoutDashboard,
  ScrollText,
  Settings,
  ShieldCheck,
  ShoppingCart,
  Sparkles,
  Truck,
  Users,
  UsersRound,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { P } from './permissions'

export interface NavItem {
  label: string
  to: string
  icon: LucideIcon
  /** Item is shown when the user holds any one of these permissions. */
  permissions: string[]
}

export interface NavSection {
  title: string
  items: NavItem[]
}

/**
 * The whole navigation tree, annotated with the permissions each entry needs.
 *
 * The sidebar is filtered from this at render time, which is why a cashier and an
 * owner see genuinely different applications rather than the same menu with
 * disabled items.
 */
export const NAVIGATION: NavSection[] = [
  {
    title: 'Overview',
    items: [
      { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard, permissions: [P.dashboardView] },
      {
        label: 'Business Intelligence',
        to: '/analytics',
        icon: BarChart3,
        permissions: [P.analyticsView],
      },
      { label: 'AI Assistant', to: '/assistant', icon: Sparkles, permissions: [P.aiAssistantUse] },
    ],
  },
  {
    title: 'Trade',
    items: [
      { label: 'Point of Sale', to: '/pos', icon: ShoppingCart, permissions: [P.posOperate] },
      { label: 'Sales', to: '/sales', icon: Banknote, permissions: [P.saleView] },
      { label: 'Customers', to: '/customers', icon: Contact, permissions: [P.customerView] },
    ],
  },
  {
    title: 'Supply',
    items: [
      { label: 'Inventory', to: '/inventory', icon: Boxes, permissions: [P.productView] },
      { label: 'Suppliers', to: '/suppliers', icon: Truck, permissions: [P.supplierView] },
      { label: 'Purchase Orders', to: '/purchases', icon: ClipboardList, permissions: [P.purchaseView] },
    ],
  },
  {
    title: 'Money',
    items: [
      { label: 'Finance', to: '/finance', icon: Gauge, permissions: [P.financeView, P.expenseView] },
      { label: 'Invoices', to: '/invoices', icon: FileText, permissions: [P.invoiceView] },
      { label: 'Accounting', to: '/accounting', icon: ScrollText, permissions: [P.accountingView] },
    ],
  },
  {
    title: 'People & Assets',
    items: [
      { label: 'Human Resources', to: '/hr', icon: UsersRound, permissions: [P.employeeView] },
      { label: 'Assets', to: '/assets', icon: HardDrive, permissions: [P.assetView] },
      { label: 'Projects', to: '/projects', icon: Building2, permissions: [P.projectView] },
    ],
  },
  {
    title: 'Insight',
    items: [{ label: 'Reports', to: '/reports', icon: BarChart3, permissions: [P.reportView] }],
  },
  {
    title: 'Administration',
    items: [
      { label: 'Users & Roles', to: '/admin/users', icon: Users, permissions: [P.userView] },
      { label: 'Audit Trail', to: '/admin/audit', icon: ShieldCheck, permissions: [P.auditView] },
      { label: 'Settings', to: '/admin/settings', icon: Settings, permissions: [P.settingsView] },
    ],
  },
]

/** Sections and items the given permission set can actually reach. */
export function visibleNavigation(permissions: string[]): NavSection[] {
  const held = new Set(permissions)
  return NAVIGATION.map((section) => ({
    title: section.title,
    items: section.items.filter((item) => item.permissions.some((code) => held.has(code))),
  })).filter((section) => section.items.length > 0)
}

/**
 * Where to send a user after sign-in.
 *
 * A cashier has no dashboard permission, so landing them on /dashboard would show
 * a permission error as the first thing they ever see. This picks the first screen
 * they can genuinely use.
 */
export function landingRouteFor(permissions: string[]): string {
  const sections = visibleNavigation(permissions)
  const first = sections[0]?.items[0]
  return first ? first.to : '/no-access'
}
