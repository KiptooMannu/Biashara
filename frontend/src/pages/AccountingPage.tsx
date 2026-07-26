import { dateShort, moneyWhole } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { ResourceTable, type Column } from '@/components/shared/ResourceTable'

interface AccountRow {
  id: number
  code: string
  name: string
  type: string
  balance: number
  debitNormal: boolean
}

interface JournalRow {
  id: number
  entryNumber: string
  entryDate: string
  description: string
  reference?: string
  totalDebit: number
  totalCredit: number
  balanced: boolean
  posted: boolean
  createdBy?: string
}

interface TrialBalance {
  assets: number
  liabilities: number
  equity: number
  revenue: number
  expenses: number
  difference: number
}

const TYPE_TONE: Record<string, 'info' | 'warning' | 'success' | 'muted' | 'danger'> = {
  ASSET: 'info',
  LIABILITY: 'warning',
  EQUITY: 'muted',
  REVENUE: 'success',
  EXPENSE: 'danger',
}

export default function AccountingPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const accounts = useApi<AccountRow[]>('/finance/accounts')
  const trial = useApi<TrialBalance>('/finance/trial-balance')
  const journal = usePagedApi<JournalRow>('/finance/journal')

  const columns: Column<JournalRow>[] = [
    {
      key: 'entry',
      header: 'Entry',
      render: (row) => (
        <div>
          <p className="font-mono font-medium">{row.entryNumber}</p>
          <p className="text-[11px] text-muted-foreground">{dateShort(row.entryDate)}</p>
        </div>
      ),
    },
    { key: 'description', header: 'Description', render: (row) => row.description },
    {
      key: 'reference',
      header: 'Reference',
      render: (row) => <span className="font-mono text-muted-foreground">{row.reference ?? '—'}</span>,
    },
    { key: 'debit', header: 'Debit', numeric: true, render: (row) => moneyWhole(row.totalDebit, currency) },
    { key: 'credit', header: 'Credit', numeric: true, render: (row) => moneyWhole(row.totalCredit, currency) },
    {
      key: 'balanced',
      header: 'Balanced',
      render: (row) => (
        <Badge variant={row.balanced ? 'success' : 'danger'}>{row.balanced ? 'Yes' : 'No'}</Badge>
      ),
    },
    {
      key: 'posted',
      header: 'Posted',
      render: (row) => <Badge variant={row.posted ? 'success' : 'muted'}>{row.posted ? 'Posted' : 'Draft'}</Badge>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Accounting"
        subtitle="Chart of accounts, double-entry journal and trial balance"
      />

      <div className="mb-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Chart of accounts</CardTitle>
            <p className="text-xs text-muted-foreground">
              1xxx assets · 2xxx liabilities · 3xxx equity · 4xxx revenue · 5xxx expenses
            </p>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={accounts.loading}
              error={accounts.error}
              empty={(accounts.data?.length ?? 0) === 0}
              onRetry={accounts.reload}
              emptyTitle="No accounts set up"
            >
              <div className="max-h-[420px] overflow-y-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Code</TableHead>
                      <TableHead>Account</TableHead>
                      <TableHead>Class</TableHead>
                      <TableHead>Normal side</TableHead>
                      <TableHead className="text-right">Balance</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(accounts.data ?? []).map((account) => (
                      <TableRow key={account.id}>
                        <TableCell className="font-mono text-xs">{account.code}</TableCell>
                        <TableCell className="text-xs font-medium">{account.name}</TableCell>
                        <TableCell>
                          <Badge variant={TYPE_TONE[account.type] ?? 'muted'}>{account.type}</Badge>
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">
                          {account.debitNormal ? 'Debit' : 'Credit'}
                        </TableCell>
                        <TableCell className="numeric text-right text-xs font-semibold">
                          {moneyWhole(account.balance, currency)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            </DataState>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Trial balance</CardTitle>
            <p className="text-xs text-muted-foreground">
              Assets should equal liabilities plus equity
            </p>
          </CardHeader>
          <CardContent className="pt-4">
            <DataState
              loading={trial.loading}
              error={trial.error}
              empty={!trial.data}
              onRetry={trial.reload}
            >
              {trial.data && (
                <div className="space-y-2 text-sm">
                  <Row label="Assets" value={moneyWhole(trial.data.assets, currency)} />
                  <Row label="Liabilities" value={moneyWhole(trial.data.liabilities, currency)} />
                  <Row label="Equity" value={moneyWhole(trial.data.equity, currency)} />
                  <div className="border-t pt-2">
                    <Row
                      label="Difference"
                      value={moneyWhole(trial.data.difference, currency)}
                      bold
                      tone={Math.abs(trial.data.difference) < 1 ? 'positive' : 'negative'}
                    />
                  </div>
                  <div className="mt-3 border-t pt-3">
                    <Row label="Revenue" value={moneyWhole(trial.data.revenue, currency)} />
                    <Row label="Expenses" value={moneyWhole(trial.data.expenses, currency)} />
                  </div>
                </div>
              )}
            </DataState>
          </CardContent>
        </Card>
      </div>

      <ResourceTable<JournalRow>
        title="Journal entries"
        subtitle="Every posting balances debits against credits"
        columns={columns}
        rows={journal.rows}
        loading={journal.loading}
        error={journal.error}
        reload={journal.reload}
        page={journal.page}
        totalPages={journal.totalPages}
        totalElements={journal.totalElements}
        onPageChange={journal.setPage}
        emptyTitle="No journal entries"
      />
    </>
  )
}

function Row({
  label,
  value,
  bold,
  tone,
}: {
  label: string
  value: string
  bold?: boolean
  tone?: 'positive' | 'negative'
}) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className={bold ? 'font-semibold' : 'text-muted-foreground'}>{label}</span>
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
    </div>
  )
}
