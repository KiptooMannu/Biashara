import { dateShort, moneyCompact, moneyWhole, number, percent } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Chart, compactMoney } from '@/components/charts/Chart'

interface ExpenseRow {
  id: number
  expenseNumber: string
  category: string
  description: string
  amount: number
  expenseDate: string
  paymentMethod?: string
  vendor?: string
  status: string
  department?: string
  recurring: boolean
  createdBy?: string
  approvedBy?: string
}

interface FinanceSummary {
  monthExpenses: number
  pendingExpenses: number
  receivables: number
  overdueInvoices: number
  collectedThisMonth: number
  paymentMix: { label: string; value: number; count: number }[]
}

interface ProfitAndLoss {
  from: string
  to: string
  revenue: number
  costOfGoodsSold: number
  grossProfit: number
  grossMarginPercent: number
  operatingExpenses: { label: string; value: number }[]
  totalOperatingExpenses: number
  netProfit: number
  netMarginPercent: number
}

export default function FinancePage() {
  const { user, can } = useAuth()
  const currency = user?.currency ?? 'KES'

  const summary = useApi<FinanceSummary>('/finance/summary')
  const paged = usePagedApi<ExpenseRow>('/finance/expenses')
  // The P&L needs a stronger permission than the expense list. The hook is always
  // called and simply told not to fetch, so hook order stays stable.
  const canSeePnl = can('report.financial')
  const pnl = useApi<ProfitAndLoss>('/finance/profit-and-loss', undefined, canSeePnl)

  const columns: Column<ExpenseRow>[] = [
    {
      key: 'ref',
      header: 'Reference',
      render: (row) => (
        <div>
          <p className="font-mono font-medium">{row.expenseNumber}</p>
          <p className="text-[11px] text-muted-foreground">{dateShort(row.expenseDate)}</p>
        </div>
      ),
    },
    { key: 'category', header: 'Category', render: (row) => row.category },
    {
      key: 'description',
      header: 'Description',
      render: (row) => (
        <div className="max-w-[240px]">
          <p className="truncate">{row.description}</p>
          {row.recurring && <p className="text-[11px] text-muted-foreground">Recurring monthly</p>}
        </div>
      ),
    },
    {
      key: 'department',
      header: 'Department',
      render: (row) => <span className="text-muted-foreground">{row.department ?? '—'}</span>,
    },
    { key: 'method', header: 'Paid by', render: (row) => <StatusBadge status={row.paymentMethod} /> },
    {
      key: 'amount',
      header: 'Amount',
      numeric: true,
      render: (row) => <span className="font-semibold">{moneyWhole(row.amount, currency)}</span>,
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    {
      key: 'approved',
      header: 'Approved by',
      render: (row) => <span className="text-muted-foreground">{row.approvedBy ?? '—'}</span>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Finance"
        subtitle="Expenses, receivables and the profit and loss position"
      />

      <StatStrip
        stats={[
          { label: 'Expenses this month', value: moneyCompact(summary.data?.monthExpenses, currency) },
          {
            label: 'Awaiting approval',
            value: number(summary.data?.pendingExpenses),
            tone: (summary.data?.pendingExpenses ?? 0) > 0 ? 'warning' : 'default',
          },
          {
            label: 'Owed to you',
            value: moneyCompact(summary.data?.receivables, currency),
            tone: 'warning',
          },
          {
            label: 'Overdue invoices',
            value: number(summary.data?.overdueInvoices),
            tone: (summary.data?.overdueInvoices ?? 0) > 0 ? 'danger' : 'success',
          },
          {
            label: 'Collected this month',
            value: moneyCompact(summary.data?.collectedThisMonth, currency),
            tone: 'success',
          },
        ]}
      />

      {canSeePnl && (
        <div className="mb-4 grid gap-4 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader className="border-b py-4">
              <CardTitle className="text-sm">Profit and loss</CardTitle>
              <p className="text-xs text-muted-foreground">
                Last 30 days. Revenue and cost of goods come from the sales ledger.
              </p>
            </CardHeader>
            <CardContent className="pt-4">
              <DataState
                loading={pnl.loading}
                error={pnl.error}
                empty={!pnl.data}
                onRetry={pnl.reload}
              >
                {pnl.data && (
                  <div className="space-y-2 text-sm">
                    <Line label="Revenue" value={moneyWhole(pnl.data.revenue, currency)} />
                    <Line
                      label="Cost of goods sold"
                      value={`(${moneyWhole(pnl.data.costOfGoodsSold, currency)})`}
                      muted
                    />
                    <Line
                      label="Gross profit"
                      value={moneyWhole(pnl.data.grossProfit, currency)}
                      badge={percent(pnl.data.grossMarginPercent)}
                      bold
                      divider
                    />
                    <Line
                      label="Operating expenses"
                      value={`(${moneyWhole(pnl.data.totalOperatingExpenses, currency)})`}
                      muted
                    />
                    <Line
                      label="Net profit"
                      value={moneyWhole(pnl.data.netProfit, currency)}
                      badge={percent(pnl.data.netMarginPercent)}
                      bold
                      divider
                      tone={pnl.data.netProfit >= 0 ? 'positive' : 'negative'}
                    />
                  </div>
                )}
              </DataState>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="border-b py-4">
              <CardTitle className="text-sm">How money comes in</CardTitle>
              <p className="text-xs text-muted-foreground">Collections, last 30 days</p>
            </CardHeader>
            <CardContent className="pt-4">
              {(summary.data?.paymentMix?.length ?? 0) === 0 ? (
                <p className="py-10 text-center text-xs text-muted-foreground">No payments yet</p>
              ) : (
                <Chart
                  type="donut"
                  height={240}
                  series={(summary.data?.paymentMix ?? []).map((entry) => Number(entry.value))}
                  options={{
                    labels: (summary.data?.paymentMix ?? []).map((entry) =>
                      entry.label
                        .toLowerCase()
                        .split('_')
                        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
                        .join(' '),
                    ),
                    legend: { position: 'bottom', fontSize: '11px' },
                    tooltip: { y: { formatter: (value) => moneyWhole(value, currency) } },
                    yaxis: { labels: { formatter: (value) => compactMoney(value, currency) } },
                  }}
                />
              )}
            </CardContent>
          </Card>
        </div>
      )}

      <ResourceTable<ExpenseRow>
        title="Expenses"
        columns={columns}
        rows={paged.rows}
        loading={paged.loading}
        error={paged.error}
        reload={paged.reload}
        page={paged.page}
        totalPages={paged.totalPages}
        totalElements={paged.totalElements}
        onPageChange={paged.setPage}
        emptyTitle="No expenses recorded"
      />
    </>
  )
}

function Line({
  label,
  value,
  badge,
  bold,
  muted,
  divider,
  tone,
}: {
  label: string
  value: string
  badge?: string
  bold?: boolean
  muted?: boolean
  divider?: boolean
  tone?: 'positive' | 'negative'
}) {
  return (
    <div className={divider ? 'border-t pt-2' : undefined}>
      <div className="flex items-baseline justify-between gap-3">
        <span className={bold ? 'font-semibold' : muted ? 'text-muted-foreground' : ''}>{label}</span>
        <span className="flex items-baseline gap-2">
          {badge && <span className="text-[11px] text-muted-foreground">{badge}</span>}
          <span
            className={[
              'numeric',
              bold ? 'font-bold' : '',
              tone === 'positive' ? 'text-emerald-600' : '',
              tone === 'negative' ? 'text-red-600' : '',
            ].join(' ')}
          >
            {value}
          </span>
        </span>
      </div>
    </div>
  )
}
