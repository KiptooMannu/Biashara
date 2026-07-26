import { useState } from 'react'
import { AlertTriangle, Boxes, PackageX, TrendingDown } from 'lucide-react'
import { moneyCompact, moneyWhole, number, percent, dateShort } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import type { ProductRow } from '@/lib/types'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'

interface InventorySummary {
  totalProducts: number
  stockValue: number
  lowStockCount: number
  outOfStockCount: number
}

type View = 'all' | 'low-stock' | 'stockout-risk' | 'dead-stock' | 'expiring'

export default function InventoryPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const [view, setView] = useState<View>('all')

  const summary = useApi<InventorySummary>('/inventory/summary')
  const paged = usePagedApi<ProductRow>('/inventory/products')
  // The filtered views are small lists rather than paged results.
  const filtered = useApi<ProductRow[]>(
    view === 'all' ? '/inventory/low-stock' : `/inventory/${view}`,
  )

  const columns: Column<ProductRow>[] = [
    {
      key: 'product',
      header: 'Product',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{row.name}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{row.sku}</p>
        </div>
      ),
    },
    {
      key: 'category',
      header: 'Category',
      render: (row) => <span className="text-muted-foreground">{row.category ?? '—'}</span>,
    },
    { key: 'buy', header: 'Cost', numeric: true, render: (row) => moneyWhole(row.buyingPrice, currency) },
    { key: 'sell', header: 'Price', numeric: true, render: (row) => moneyWhole(row.sellingPrice, currency) },
    {
      key: 'margin',
      header: 'Margin',
      numeric: true,
      render: (row) => (
        <Badge variant={row.marginPercent >= 25 ? 'success' : row.marginPercent >= 15 ? 'warning' : 'danger'}>
          {percent(row.marginPercent, 0)}
        </Badge>
      ),
    },
    {
      key: 'stock',
      header: 'Stock',
      numeric: true,
      render: (row) => (
        <span
          className={
            row.outOfStock ? 'font-bold text-red-600' : row.lowStock ? 'font-semibold text-amber-600' : ''
          }
        >
          {number(row.currentStock)} {row.unit}
        </span>
      ),
    },
    {
      key: 'velocity',
      header: 'Per day',
      numeric: true,
      render: (row) =>
        row.salesVelocity != null && row.salesVelocity > 0 ? (
          <span>{row.salesVelocity}</span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: 'cover',
      header: 'Days left',
      numeric: true,
      render: (row) =>
        row.daysUntilStockout != null ? (
          <Badge variant={row.daysUntilStockout <= 3 ? 'danger' : row.daysUntilStockout <= 7 ? 'warning' : 'muted'}>
            {row.daysUntilStockout}d
          </Badge>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: 'value',
      header: 'Stock value',
      numeric: true,
      render: (row) => moneyWhole(row.stockValue, currency),
    },
    {
      key: 'expiry',
      header: 'Expires',
      render: (row) =>
        row.expiryDate ? (
          <span className="text-muted-foreground">{dateShort(row.expiryDate)}</span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
  ]

  const showingFiltered = view !== 'all'
  const rows = showingFiltered ? (filtered.data ?? []) : paged.rows

  return (
    <>
      <PageHeader
        title="Inventory"
        subtitle="Stock levels, valuation and stockout prediction from measured sales velocity"
      />

      <StatStrip
        stats={[
          { label: 'Products', value: number(summary.data?.totalProducts) },
          { label: 'Stock value', value: moneyCompact(summary.data?.stockValue, currency) },
          {
            label: 'Low stock',
            value: number(summary.data?.lowStockCount),
            tone: (summary.data?.lowStockCount ?? 0) > 0 ? 'warning' : 'default',
          },
          {
            label: 'Out of stock',
            value: number(summary.data?.outOfStockCount),
            tone: (summary.data?.outOfStockCount ?? 0) > 0 ? 'danger' : 'success',
          },
        ]}
      />

      <Tabs value={view} onValueChange={(value) => setView(value as View)} className="mb-4">
        <TabsList>
          <TabsTrigger value="all">
            <Boxes className="mr-1.5 h-3.5 w-3.5" /> All products
          </TabsTrigger>
          <TabsTrigger value="low-stock">
            <AlertTriangle className="mr-1.5 h-3.5 w-3.5" /> Low stock
          </TabsTrigger>
          <TabsTrigger value="stockout-risk">
            <TrendingDown className="mr-1.5 h-3.5 w-3.5" /> Running out
          </TabsTrigger>
          <TabsTrigger value="dead-stock">
            <PackageX className="mr-1.5 h-3.5 w-3.5" /> Dead stock
          </TabsTrigger>
          <TabsTrigger value="expiring">Expiring</TabsTrigger>
        </TabsList>
      </Tabs>

      <ResourceTable<ProductRow>
        title={VIEW_TITLES[view]}
        subtitle={VIEW_SUBTITLES[view]}
        columns={columns}
        rows={rows}
        loading={showingFiltered ? filtered.loading : paged.loading}
        error={showingFiltered ? filtered.error : paged.error}
        reload={showingFiltered ? filtered.reload : paged.reload}
        search={showingFiltered ? undefined : paged.search}
        onSearchChange={showingFiltered ? undefined : paged.setSearch}
        searchPlaceholder="Name, SKU or barcode"
        page={showingFiltered ? undefined : paged.page}
        totalPages={showingFiltered ? undefined : paged.totalPages}
        totalElements={showingFiltered ? rows.length : paged.totalElements}
        onPageChange={paged.setPage}
        emptyTitle={VIEW_EMPTY[view]}
      />
    </>
  )
}

const VIEW_TITLES: Record<View, string> = {
  all: 'All products',
  'low-stock': 'At or below minimum level',
  'stockout-risk': 'Predicted to run out soonest',
  'dead-stock': 'No movement in 30 days',
  expiring: 'Expiring within 30 days',
}

const VIEW_SUBTITLES: Record<View, string> = {
  all: 'The full catalogue',
  'low-stock': 'Reorder these before they run out',
  'stockout-risk': 'Ranked by current stock divided by daily sales velocity',
  'dead-stock': 'Capital tied up in stock that is not selling',
  expiring: 'Clear or discount these to avoid a write-off',
}

const VIEW_EMPTY: Record<View, string> = {
  all: 'No products yet',
  'low-stock': 'Everything is above its minimum level',
  'stockout-risk': 'Nothing is at immediate risk',
  'dead-stock': 'Every line with stock has sold recently',
  expiring: 'Nothing expires in the next 30 days',
}
