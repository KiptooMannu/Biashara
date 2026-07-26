import { dateShort, moneyCompact, moneyWhole, number, percent } from '@/lib/format'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface AssetRow {
  id: number
  assetTag: string
  name: string
  category?: string
  serialNumber?: string
  purchaseDate: string
  purchaseCost: number
  depreciationRate?: number
  currentValue: number
  status: string
  assignedTo?: string
  branch?: string
  warrantyExpiry?: string
  underWarranty: boolean
  nextServiceDate?: string
  serviceDue: boolean
}

interface AssetSummary {
  totalAssets: number
  purchaseCost: number
  bookValue: number
  serviceDue: number
}

export default function AssetsPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const summary = useApi<AssetSummary>('/assets/summary')
  const assets = useApi<AssetRow[]>('/assets')

  const columns: Column<AssetRow>[] = [
    {
      key: 'asset',
      header: 'Asset',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{row.name}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{row.assetTag}</p>
        </div>
      ),
    },
    {
      key: 'category',
      header: 'Category',
      render: (row) => <Badge variant="secondary">{row.category ?? '—'}</Badge>,
    },
    { key: 'purchased', header: 'Purchased', render: (row) => dateShort(row.purchaseDate) },
    {
      key: 'cost',
      header: 'Cost',
      numeric: true,
      render: (row) => moneyWhole(row.purchaseCost, currency),
    },
    {
      key: 'rate',
      header: 'Depreciation',
      numeric: true,
      render: (row) => (row.depreciationRate != null ? `${row.depreciationRate}%/yr` : '—'),
    },
    {
      key: 'value',
      header: 'Book value',
      numeric: true,
      render: (row) => (
        <div>
          <p className="font-semibold">{moneyWhole(row.currentValue, currency)}</p>
          <p className="text-[11px] text-muted-foreground">
            {percent((row.currentValue / row.purchaseCost) * 100, 0)} of cost
          </p>
        </div>
      ),
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    {
      key: 'assigned',
      header: 'Assigned to',
      render: (row) => <span className="text-muted-foreground">{row.assignedTo ?? 'Unassigned'}</span>,
    },
    {
      key: 'warranty',
      header: 'Warranty',
      render: (row) =>
        row.warrantyExpiry ? (
          <Badge variant={row.underWarranty ? 'success' : 'muted'}>
            {row.underWarranty ? `to ${dateShort(row.warrantyExpiry)}` : 'Expired'}
          </Badge>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: 'service',
      header: 'Next service',
      render: (row) =>
        row.nextServiceDate ? (
          <span className={row.serviceDue ? 'font-semibold text-red-600' : 'text-muted-foreground'}>
            {dateShort(row.nextServiceDate)}
            {row.serviceDue && ' (due)'}
          </span>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Assets"
        subtitle="Register with straight-line book value computed as at today, floored at salvage value"
      />

      <StatStrip
        stats={[
          { label: 'Assets', value: number(summary.data?.totalAssets) },
          { label: 'Purchase cost', value: moneyCompact(summary.data?.purchaseCost, currency) },
          {
            label: 'Book value today',
            value: moneyCompact(summary.data?.bookValue, currency),
            tone: 'success',
          },
          {
            label: 'Service overdue',
            value: number(summary.data?.serviceDue),
            tone: (summary.data?.serviceDue ?? 0) > 0 ? 'danger' : 'success',
          },
        ]}
      />

      <ResourceTable<AssetRow>
        title="Asset register"
        columns={columns}
        rows={assets.data ?? []}
        loading={assets.loading}
        error={assets.error}
        reload={assets.reload}
        totalElements={assets.data?.length}
        emptyTitle="No assets recorded"
      />
    </>
  )
}
