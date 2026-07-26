import { dateShort, moneyWhole, number } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface InvoiceRow {
  id: number
  invoiceNumber: string
  customerName: string
  issueDate: string
  dueDate: string
  total: number
  amountPaid?: number
  balance: number
  status: string
  overdue: boolean
  daysOverdue: number
}

interface FinanceSummary {
  receivables: number
  overdueInvoices: number
  collectedThisMonth: number
}

export default function InvoicesPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const summary = useApi<FinanceSummary>('/finance/summary')
  const paged = usePagedApi<InvoiceRow>('/finance/invoices')

  const columns: Column<InvoiceRow>[] = [
    {
      key: 'ref',
      header: 'Invoice',
      render: (row) => (
        <div>
          <p className="font-mono font-medium">{row.invoiceNumber}</p>
          <p className="text-[11px] text-muted-foreground">Issued {dateShort(row.issueDate)}</p>
        </div>
      ),
    },
    { key: 'customer', header: 'Customer', render: (row) => row.customerName },
    {
      key: 'due',
      header: 'Due',
      render: (row) => (
        <div>
          <p className={row.overdue ? 'font-semibold text-red-600' : ''}>{dateShort(row.dueDate)}</p>
          {row.overdue && (
            <p className="text-[11px] text-red-600">{number(row.daysOverdue)} days late</p>
          )}
        </div>
      ),
    },
    { key: 'total', header: 'Total', numeric: true, render: (row) => moneyWhole(row.total, currency) },
    {
      key: 'paid',
      header: 'Paid',
      numeric: true,
      render: (row) => (
        <span className="text-emerald-600">{moneyWhole(row.amountPaid ?? 0, currency)}</span>
      ),
    },
    {
      key: 'balance',
      header: 'Balance',
      numeric: true,
      render: (row) =>
        row.balance > 0 ? (
          <span className="font-bold text-amber-600">{moneyWhole(row.balance, currency)}</span>
        ) : (
          <Badge variant="success">Settled</Badge>
        ),
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  return (
    <>
      <PageHeader title="Invoices" subtitle="Receivables, ageing and collection status" />

      <StatStrip
        stats={[
          { label: 'Owed to you', value: moneyWhole(summary.data?.receivables, currency), tone: 'warning' },
          {
            label: 'Overdue invoices',
            value: number(summary.data?.overdueInvoices),
            tone: (summary.data?.overdueInvoices ?? 0) > 0 ? 'danger' : 'success',
          },
          {
            label: 'Collected this month',
            value: moneyWhole(summary.data?.collectedThisMonth, currency),
            tone: 'success',
          },
        ]}
      />

      <ResourceTable<InvoiceRow>
        title="All invoices"
        columns={columns}
        rows={paged.rows}
        loading={paged.loading}
        error={paged.error}
        reload={paged.reload}
        page={paged.page}
        totalPages={paged.totalPages}
        totalElements={paged.totalElements}
        onPageChange={paged.setPage}
        emptyTitle="No invoices issued"
      />
    </>
  )
}
