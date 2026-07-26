import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { Loader2, ShieldAlert } from 'lucide-react'
import { Toaster } from '@/components/ui/sonner'
import { AppLayout } from '@/components/layout/AppLayout'
import { landingRouteFor } from '@/lib/navigation'
import { useAuth } from '@/store/auth'
import { Button } from '@/components/ui/button'

import LoginPage from '@/pages/LoginPage'
import ChangePasswordPage from '@/pages/ChangePasswordPage'
import DashboardPage from '@/pages/DashboardPage'
import PosPage from '@/pages/PosPage'
import SalesPage from '@/pages/SalesPage'
import CustomersPage from '@/pages/CustomersPage'
import InventoryPage from '@/pages/InventoryPage'
import SuppliersPage from '@/pages/SuppliersPage'
import PurchasesPage from '@/pages/PurchasesPage'
import FinancePage from '@/pages/FinancePage'
import InvoicesPage from '@/pages/InvoicesPage'
import AccountingPage from '@/pages/AccountingPage'
import HrPage from '@/pages/HrPage'
import AssetsPage from '@/pages/AssetsPage'
import ProjectsPage from '@/pages/ProjectsPage'
import ReportsPage from '@/pages/ReportsPage'
import AnalyticsPage from '@/pages/AnalyticsPage'
import AssistantPage from '@/pages/AssistantPage'
import UsersPage from '@/pages/UsersPage'
import AuditPage from '@/pages/AuditPage'
import SettingsPage from '@/pages/SettingsPage'
import ProfilePage from '@/pages/ProfilePage'

/** Blocks a route until the session is known, then until permission is held. */
function Guarded({
  permission,
  children,
}: {
  permission?: string
  children: React.ReactNode
}) {
  const { user, bootstrapping, mustChangePassword, can } = useAuth()
  const location = useLocation()

  if (bootstrapping) {
    return <FullPageSpinner />
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  // A forced password change takes precedence over everything else.
  if (mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />
  }
  if (permission && !can(permission)) {
    return <NoPermission />
  }
  return <>{children}</>
}

function FullPageSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="flex flex-col items-center gap-3">
        <Loader2 className="h-6 w-6 animate-spin text-primary" />
        <p className="text-xs text-muted-foreground">Loading BIASHARA…</p>
      </div>
    </div>
  )
}

function NoPermission() {
  const user = useAuth((state) => state.user)
  return (
    <div className="flex min-h-[70vh] flex-col items-center justify-center px-6 text-center">
      <div className="mb-4 rounded-full bg-amber-100 p-3.5">
        <ShieldAlert className="h-7 w-7 text-amber-600" />
      </div>
      <h2 className="text-lg font-bold">You do not have access to this</h2>
      <p className="mt-2 max-w-md text-sm text-muted-foreground">
        Your role ({user?.primaryRoleName}) does not include permission for this screen.
        This is role-based access control working as intended — sign in as another role to
        see it.
      </p>
      <Button asChild variant="outline" className="mt-5">
        <a href={landingRouteFor(user?.permissions ?? [])}>Back to my dashboard</a>
      </Button>
    </div>
  )
}

/** Sends a signed-in user to the first screen their role can actually use. */
function LandingRedirect() {
  const { user, bootstrapping } = useAuth()
  if (bootstrapping) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace />
  return <Navigate to={landingRouteFor(user.permissions)} replace />
}

export default function App() {
  const bootstrap = useAuth((state) => state.bootstrap)

  useEffect(() => {
    void bootstrap()
  }, [bootstrap])

  return (
    <>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/change-password"
          element={
            <Guarded>
              <ChangePasswordPage />
            </Guarded>
          }
        />

        <Route
          element={
            <Guarded>
              <AppLayout />
            </Guarded>
          }
        >
          <Route path="/" element={<LandingRedirect />} />
          <Route path="/dashboard" element={<Guarded permission="dashboard.view"><DashboardPage /></Guarded>} />
          <Route path="/analytics" element={<Guarded permission="analytics.view"><AnalyticsPage /></Guarded>} />
          <Route path="/assistant" element={<Guarded permission="ai.assistant.use"><AssistantPage /></Guarded>} />

          <Route path="/pos" element={<Guarded permission="sales.pos.operate"><PosPage /></Guarded>} />
          <Route path="/sales" element={<Guarded permission="sales.sale.view"><SalesPage /></Guarded>} />
          <Route path="/customers" element={<Guarded permission="crm.customer.view"><CustomersPage /></Guarded>} />

          <Route path="/inventory" element={<Guarded permission="inventory.product.view"><InventoryPage /></Guarded>} />
          <Route path="/suppliers" element={<Guarded permission="procurement.supplier.view"><SuppliersPage /></Guarded>} />
          <Route path="/purchases" element={<Guarded permission="procurement.purchase.view"><PurchasesPage /></Guarded>} />

          <Route path="/finance" element={<Guarded permission="finance.expense.view"><FinancePage /></Guarded>} />
          <Route path="/invoices" element={<Guarded permission="finance.invoice.view"><InvoicesPage /></Guarded>} />
          <Route path="/accounting" element={<Guarded permission="finance.accounting.view"><AccountingPage /></Guarded>} />

          <Route path="/hr" element={<Guarded permission="hr.employee.view"><HrPage /></Guarded>} />
          <Route path="/assets" element={<Guarded permission="asset.view"><AssetsPage /></Guarded>} />
          <Route path="/projects" element={<Guarded permission="project.view"><ProjectsPage /></Guarded>} />

          <Route path="/reports" element={<Guarded permission="report.view"><ReportsPage /></Guarded>} />

          <Route path="/admin/users" element={<Guarded permission="admin.user.view"><UsersPage /></Guarded>} />
          <Route path="/admin/audit" element={<Guarded permission="admin.audit.view"><AuditPage /></Guarded>} />
          <Route path="/admin/settings" element={<Guarded permission="admin.settings.view"><SettingsPage /></Guarded>} />

          <Route path="/profile" element={<Guarded><ProfilePage /></Guarded>} />
          <Route path="/no-access" element={<NoPermission />} />
        </Route>

        <Route path="*" element={<LandingRedirect />} />
      </Routes>

      <Toaster position="top-right" richColors />
    </>
  )
}
