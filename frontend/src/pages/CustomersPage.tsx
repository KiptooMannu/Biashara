import { moneyCompact, moneyWhole, number, percent, timeAgo } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface CustomerRow {
  id: number
  name: string
  phone?: string
  email?: string
  customerType?: string
  tier?: string
  loyaltyPoints?: number
  creditLimit?: number
  outstandingBalance?: number
  totalSpent?: number
  totalOrders?: number
  averageOrderValue?: number
  lastPurchaseAt?: string
  churnRisk?: number
  lifetimeValue?: number
  rfmSegment?: string
  overCreditLimit: boolean
}

interface CrmSummary {
  totalCustomers: number
  totalReceivables: number
}

export default function CustomersPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const summary = useApi<CrmSummary>('/customers/summary')
  const paged = usePagedApi<CustomerRow>('/customers')

  const columns: Column<CustomerRow>[] = [
    {
      key: 'name',
      header: 'Customer',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{row.name}</p>
          <p className="text-[11px] text-muted-foreground">{row.phone ?? row.email ?? '—'}</p>
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (row) => <Badge variant="secondary">{row.customerType ?? 'INDIVIDUAL'}</Badge>,
    },
    {
      key: 'tier',
      header: 'Tier',
      render: (row) => <StatusBadge status={row.tier} />,
    },
    {
      key: 'rfm',
      header: 'RFM',
      render: (row) =>
        row.rfmSegment ? (
          <span className="numeric font-mono text-muted-foreground">{row.rfmSegment}</span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    { key: 'orders', header: 'Orders', numeric: true, render: (row) => number(row.totalOrders) },
    {
      key: 'spent',
      header: 'Total spent',
      numeric: true,
      render: (row) => <span className="font-semibold">{moneyWhole(row.totalSpent, currency)}</span>,
    },
    {
      key: 'aov',
      header: 'Avg order',
      numeric: true,
      render: (row) => moneyWhole(row.averageOrderValue, currency),
    },
    {
      key: 'ltv',
      header: 'Lifetime value',
      numeric: true,
      render: (row) => <span className="text-muted-foreground">{moneyCompact(row.lifetimeValue, currency)}</span>,
    },
    {
      key: 'owing',
      header: 'Owes',
      numeric: true,
      render: (row) =>
        (row.outstandingBalance ?? 0) > 0 ? (
          <span className={row.overCreditLimit ? 'font-bold text-red-600' : 'font-semibold text-amber-600'}>
            {moneyWhole(row.outstandingBalance, currency)}
          </span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: 'churn',
      header: 'Churn risk',
      numeric: true,
      render: (row) =>
        row.churnRisk != null ? (
          <Badge variant={row.churnRisk >= 70 ? 'danger' : row.churnRisk >= 40 ? 'warning' : 'success'}>
            {percent(row.churnRisk, 0)}
          </Badge>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    { key: 'points', header: 'Points', numeric: true, render: (row) => number(row.loyaltyPoints) },
    {
      key: 'last',
      header: 'Last purchase',
      render: (row) => <span className="text-muted-foreground">{timeAgo(row.lastPurchaseAt)}</span>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Customers"
        subtitle="Tiers, churn risk and lifetime value scored from actual purchase history, not assigned by hand"
      />

      <StatStrip
        stats={[
          { label: 'Customers', value: number(summary.data?.totalCustomers) },
          {
            label: 'Owed to you',
            value: moneyCompact(summary.data?.totalReceivables, currency),
            tone: (summary.data?.totalReceivables ?? 0) > 0 ? 'warning' : 'default',
          },
        ]}
      />

      <ResourceTable<CustomerRow>
        title="Customer accounts"
        columns={columns}
        rows={paged.rows}
        loading={paged.loading}
        error={paged.error}
        reload={paged.reload}
        search={paged.search}
        onSearchChange={paged.setSearch}
        searchPlaceholder="Name, phone or email"
        page={paged.page}
        totalPages={paged.totalPages}
        totalElements={paged.totalElements}
        onPageChange={paged.setPage}
        emptyTitle="No customers yet"
      />
    </>
  )
}
