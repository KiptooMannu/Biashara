import { moneyCompact, moneyWhole, number, dateTime } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import type { SaleRow } from '@/lib/types'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface SalesSummary {
  todayRevenue: number
  todayOrders: number
  monthRevenue: number
  monthProfit: number
  monthOrders: number
  totalSales: number
}

export default function SalesPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const summary = useApi<SalesSummary>('/sales/summary')
  const paged = usePagedApi<SaleRow>('/sales')

  const columns: Column<SaleRow>[] = [
    {
      key: 'invoice',
      header: 'Invoice',
      render: (row) => (
        <div>
          <p className="font-mono font-medium">{row.invoiceNumber}</p>
          <p className="text-[11px] text-muted-foreground">{dateTime(row.saleDate)}</p>
        </div>
      ),
    },
    { key: 'customer', header: 'Customer', render: (row) => row.customerName ?? 'Walk-in' },
    {
      key: 'cashier',
      header: 'Served by',
      render: (row) => <span className="text-muted-foreground">{row.cashierName ?? '—'}</span>,
    },
    {
      key: 'branch',
      header: 'Branch',
      render: (row) => <span className="text-muted-foreground">{row.branchName ?? '—'}</span>,
    },
    { key: 'subtotal', header: 'Net', numeric: true, render: (row) => moneyWhole(row.subtotal, currency) },
    { key: 'tax', header: 'VAT', numeric: true, render: (row) => moneyWhole(row.taxAmount, currency) },
    {
      key: 'total',
      header: 'Total',
      numeric: true,
      render: (row) => <span className="font-semibold">{moneyWhole(row.total, currency)}</span>,
    },
    {
      key: 'profit',
      header: 'Gross profit',
      numeric: true,
      render: (row) => (
        <span className={row.grossProfit >= 0 ? 'text-emerald-600' : 'text-red-600'}>
          {moneyWhole(row.grossProfit, currency)}
        </span>
      ),
    },
    { key: 'method', header: 'Paid by', render: (row) => <StatusBadge status={row.paymentMethod} /> },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.paymentStatus} /> },
  ]

  return (
    <>
      <PageHeader
        title="Sales"
        subtitle="Every recorded sale, with gross profit calculated from the cost captured at the time of sale"
      />

      <StatStrip
        stats={[
          { label: "Today's revenue", value: moneyCompact(summary.data?.todayRevenue, currency) },
          { label: 'Orders today', value: number(summary.data?.todayOrders) },
          { label: 'Month revenue', value: moneyCompact(summary.data?.monthRevenue, currency) },
          {
            label: 'Month gross profit',
            value: moneyCompact(summary.data?.monthProfit, currency),
            tone: 'success',
          },
          { label: 'Orders this month', value: number(summary.data?.monthOrders) },
          { label: 'Sales on record', value: number(summary.data?.totalSales) },
        ]}
      />

      <ResourceTable<SaleRow>
        title="Sales history"
        columns={columns}
        rows={paged.rows}
        loading={paged.loading}
        error={paged.error}
        reload={paged.reload}
        page={paged.page}
        totalPages={paged.totalPages}
        totalElements={paged.totalElements}
        onPageChange={paged.setPage}
        emptyTitle="No sales recorded yet"
      />
    </>
  )
}
