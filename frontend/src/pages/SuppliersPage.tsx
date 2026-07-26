import { moneyCompact, moneyWhole, number, percent } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'

interface SupplierRow {
  id: number
  name: string
  code?: string
  contactPerson?: string
  phone?: string
  email?: string
  leadTimeDays?: number
  averageDeliveryDays?: number
  reliabilityScore?: number
  onTimeRate?: number
  rating?: number
  totalOrders?: number
  lateDeliveries?: number
  totalPurchaseValue?: number
  outstandingBalance?: number
  paymentTerms?: string
}

interface ProcurementSummary {
  totalSuppliers: number
  totalPurchaseOrders: number
  openOrders: number
  overdueDeliveries: number
  totalPayables: number
  spendThisMonth: number
}

export default function SuppliersPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const summary = useApi<ProcurementSummary>('/procurement/summary')
  const paged = usePagedApi<SupplierRow>('/procurement/suppliers')

  const columns: Column<SupplierRow>[] = [
    {
      key: 'name',
      header: 'Supplier',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{row.name}</p>
          <p className="text-[11px] text-muted-foreground">{row.contactPerson ?? row.code ?? '—'}</p>
        </div>
      ),
    },
    { key: 'phone', header: 'Contact', render: (row) => row.phone ?? row.email ?? '—' },
    {
      key: 'rating',
      header: 'Rating',
      render: (row) => (
        <span className="text-amber-500" title={`${row.rating ?? 0} of 5`}>
          {'★'.repeat(row.rating ?? 0)}
          <span className="text-muted-foreground/40">{'★'.repeat(5 - (row.rating ?? 0))}</span>
        </span>
      ),
    },
    {
      key: 'reliability',
      header: 'On-time',
      numeric: true,
      render: (row) =>
        row.reliabilityScore != null ? (
          <Badge
            variant={
              row.reliabilityScore >= 90 ? 'success' : row.reliabilityScore >= 75 ? 'warning' : 'danger'
            }
          >
            {percent(row.reliabilityScore, 0)}
          </Badge>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: 'lead',
      header: 'Lead time',
      numeric: true,
      render: (row) => (
        <span>
          {row.leadTimeDays ?? '—'}d
          {row.averageDeliveryDays != null && (
            <span
              className={
                (row.averageDeliveryDays ?? 0) > (row.leadTimeDays ?? 0)
                  ? 'ml-1 text-red-600'
                  : 'ml-1 text-muted-foreground'
              }
            >
              (actual {row.averageDeliveryDays}d)
            </span>
          )}
        </span>
      ),
    },
    { key: 'orders', header: 'Orders', numeric: true, render: (row) => number(row.totalOrders) },
    {
      key: 'late',
      header: 'Late',
      numeric: true,
      render: (row) =>
        (row.lateDeliveries ?? 0) > 0 ? (
          <span className="font-semibold text-amber-600">{row.lateDeliveries}</span>
        ) : (
          <span className="text-muted-foreground">0</span>
        ),
    },
    {
      key: 'spend',
      header: 'Total spend',
      numeric: true,
      render: (row) => moneyWhole(row.totalPurchaseValue, currency),
    },
    {
      key: 'owed',
      header: 'You owe',
      numeric: true,
      render: (row) =>
        (row.outstandingBalance ?? 0) > 0 ? (
          <span className="font-semibold text-amber-600">
            {moneyWhole(row.outstandingBalance, currency)}
          </span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    { key: 'terms', header: 'Terms', render: (row) => row.paymentTerms ?? '—' },
  ]

  return (
    <>
      <PageHeader
        title="Suppliers"
        subtitle="Scorecards built from observed delivery performance against agreed lead times"
      />

      <StatStrip
        stats={[
          { label: 'Suppliers', value: number(summary.data?.totalSuppliers) },
          { label: 'Purchase orders', value: number(summary.data?.totalPurchaseOrders) },
          { label: 'Open orders', value: number(summary.data?.openOrders), tone: 'warning' },
          {
            label: 'Overdue deliveries',
            value: number(summary.data?.overdueDeliveries),
            tone: (summary.data?.overdueDeliveries ?? 0) > 0 ? 'danger' : 'success',
          },
          { label: 'You owe', value: moneyCompact(summary.data?.totalPayables, currency), tone: 'warning' },
          { label: 'Spend this month', value: moneyCompact(summary.data?.spendThisMonth, currency) },
        ]}
      />

      <ResourceTable<SupplierRow>
        title="Supplier register"
        columns={columns}
        rows={paged.rows}
        loading={paged.loading}
        error={paged.error}
        reload={paged.reload}
        page={paged.page}
        totalPages={paged.totalPages}
        totalElements={paged.totalElements}
        onPageChange={paged.setPage}
        emptyTitle="No suppliers yet"
      />
    </>
  )
}
