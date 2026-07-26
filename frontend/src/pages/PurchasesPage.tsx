import { useState } from 'react'
import { ClipboardList, Lightbulb, TriangleAlert } from 'lucide-react'
import { dateShort, moneyCompact, moneyWhole, number } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface PurchaseRow {
  id: number
  poNumber: string
  supplierName: string
  orderDate: string
  expectedDelivery?: string
  receivedDate?: string
  status: string
  paymentStatus?: string
  total?: number
  overdue: boolean
  createdBy?: string
}

interface ReorderSuggestion {
  productId: number
  productName: string
  sku: string
  currentStock: number
  salesVelocity?: number
  daysUntilStockout?: number
  suggestedQuantity: number
  estimatedCost: number
  supplierName?: string
  leadTimeDays?: number
  urgency: string
  rationale: string
}

interface ProcurementSummary {
  totalPurchaseOrders: number
  openOrders: number
  overdueDeliveries: number
  totalPayables: number
  spendThisMonth: number
}

export default function PurchasesPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const [tab, setTab] = useState<'orders' | 'suggestions'>('orders')

  const summary = useApi<ProcurementSummary>('/procurement/summary')
  const paged = usePagedApi<PurchaseRow>('/procurement/purchases')
  const suggestions = useApi<ReorderSuggestion[]>('/procurement/reorder-suggestions')

  const columns: Column<PurchaseRow>[] = [
    {
      key: 'po',
      header: 'Order',
      render: (row) => (
        <div>
          <p className="font-mono font-medium">{row.poNumber}</p>
          <p className="text-[11px] text-muted-foreground">{dateShort(row.orderDate)}</p>
        </div>
      ),
    },
    { key: 'supplier', header: 'Supplier', render: (row) => row.supplierName },
    {
      key: 'expected',
      header: 'Expected',
      render: (row) => (
        <span className={row.overdue ? 'font-semibold text-red-600' : 'text-muted-foreground'}>
          {dateShort(row.expectedDelivery)}
          {row.overdue && ' (late)'}
        </span>
      ),
    },
    {
      key: 'received',
      header: 'Received',
      render: (row) =>
        row.receivedDate ? (
          dateShort(row.receivedDate)
        ) : (
          <span className="text-muted-foreground">Pending</span>
        ),
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    { key: 'payment', header: 'Payment', render: (row) => <StatusBadge status={row.paymentStatus} /> },
    {
      key: 'total',
      header: 'Total',
      numeric: true,
      render: (row) => <span className="font-semibold">{moneyWhole(row.total, currency)}</span>,
    },
    {
      key: 'by',
      header: 'Raised by',
      render: (row) => <span className="text-muted-foreground">{row.createdBy ?? '—'}</span>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Purchase Orders"
        subtitle="Orders, deliveries and reorder suggestions computed from sales velocity and supplier lead time"
      />

      <StatStrip
        stats={[
          { label: 'Purchase orders', value: number(summary.data?.totalPurchaseOrders) },
          { label: 'Open orders', value: number(summary.data?.openOrders), tone: 'warning' },
          {
            label: 'Overdue deliveries',
            value: number(summary.data?.overdueDeliveries),
            tone: (summary.data?.overdueDeliveries ?? 0) > 0 ? 'danger' : 'success',
          },
          { label: 'You owe suppliers', value: moneyCompact(summary.data?.totalPayables, currency) },
          { label: 'Spend this month', value: moneyCompact(summary.data?.spendThisMonth, currency) },
          {
            label: 'Reorder suggestions',
            value: number(suggestions.data?.length),
            tone: (suggestions.data?.length ?? 0) > 0 ? 'warning' : 'success',
          },
        ]}
      />

      <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="orders">
            <ClipboardList className="mr-1.5 h-3.5 w-3.5" /> Purchase orders
          </TabsTrigger>
          <TabsTrigger value="suggestions">
            <Lightbulb className="mr-1.5 h-3.5 w-3.5" /> Suggested orders
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === 'orders' ? (
        <ResourceTable<PurchaseRow>
          title="Purchase orders"
          columns={columns}
          rows={paged.rows}
          loading={paged.loading}
          error={paged.error}
          reload={paged.reload}
          page={paged.page}
          totalPages={paged.totalPages}
          totalElements={paged.totalElements}
          onPageChange={paged.setPage}
          emptyTitle="No purchase orders yet"
        />
      ) : (
        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Suggested purchase orders</CardTitle>
            <p className="text-xs text-muted-foreground">
              Quantity covers the supplier's lead time plus a fortnight of buffer at the
              product's measured daily sales rate
            </p>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={suggestions.loading}
              error={suggestions.error}
              empty={(suggestions.data?.length ?? 0) === 0}
              onRetry={suggestions.reload}
              emptyTitle="Nothing needs ordering"
              emptyMessage="No product will run out inside its supplier's lead time."
            >
              <div className="divide-y">
                {(suggestions.data ?? []).map((suggestion) => (
                  <div key={suggestion.productId} className="p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <p className="truncate text-sm font-semibold">{suggestion.productName}</p>
                          <Badge
                            variant={
                              suggestion.urgency === 'CRITICAL'
                                ? 'danger'
                                : suggestion.urgency === 'HIGH'
                                  ? 'warning'
                                  : 'muted'
                            }
                          >
                            {suggestion.urgency === 'CRITICAL' && (
                              <TriangleAlert className="mr-0.5 h-3 w-3" />
                            )}
                            {suggestion.urgency}
                          </Badge>
                        </div>
                        <p className="font-mono text-[11px] text-muted-foreground">{suggestion.sku}</p>
                      </div>
                      <div className="text-right">
                        <p className="numeric text-sm font-bold">
                          Order {number(suggestion.suggestedQuantity)}
                        </p>
                        <p className="numeric text-[11px] text-muted-foreground">
                          ≈ {moneyWhole(suggestion.estimatedCost, currency)}
                        </p>
                      </div>
                    </div>

                    <p className="numeric mt-2 text-xs text-muted-foreground">{suggestion.rationale}</p>

                    <div className="mt-2 flex flex-wrap gap-1.5">
                      <Badge variant="secondary">{suggestion.supplierName}</Badge>
                      <Badge variant="muted">Lead time {suggestion.leadTimeDays}d</Badge>
                      {suggestion.daysUntilStockout != null && (
                        <Badge variant={suggestion.daysUntilStockout <= 3 ? 'danger' : 'warning'}>
                          {suggestion.daysUntilStockout}d of cover left
                        </Badge>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </DataState>
          </CardContent>
        </Card>
      )}
    </>
  )
}
