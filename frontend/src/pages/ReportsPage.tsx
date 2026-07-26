import { useState } from 'react'
import { moneyWhole, number } from '@/lib/format'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { Chart, SERIES_COLOURS, compactMoney } from '@/components/charts/Chart'

interface Labelled {
  label: string
  value: number
  count: number
}

interface AbcRow {
  product: string
  revenue: number
  units: number
  cumulativeShare: number
  classification: 'A' | 'B' | 'C'
}

interface ReportsResponse {
  periodDays: number
  currency: string
  revenueTrend: { bucket: string; value: number; secondary: number }[]
  topProductsByRevenue: Labelled[]
  topProductsByVolume: Labelled[]
  revenueByCategory: Labelled[]
  revenueByBranch: Labelled[]
  revenueByCashier: Labelled[]
  revenueByHour: Labelled[]
  paymentMix: Labelled[]
  stockValueByCategory: Labelled[]
  customerTiers: Labelled[]
  stockMovementByType: Labelled[]
  attendanceMix: Labelled[]
  abcAnalysis: AbcRow[]
}

const PERIODS = [
  { label: '7 days', value: 7 },
  { label: '30 days', value: 30 },
  { label: '90 days', value: 90 },
]

export default function ReportsPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const [days, setDays] = useState(30)
  const reports = useApi<ReportsResponse>('/reports', { days })

  const money = (value: number) => moneyWhole(value, currency)

  return (
    <>
      <PageHeader
        title="Reports"
        subtitle="Sales, inventory, staff and customer analysis over your chosen period"
        action={
          <Tabs value={String(days)} onValueChange={(value) => setDays(Number(value))}>
            <TabsList>
              {PERIODS.map((period) => (
                <TabsTrigger key={period.value} value={String(period.value)}>
                  {period.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        }
      />

      <DataState
        loading={reports.loading}
        error={reports.error}
        empty={!reports.data}
        onRetry={reports.reload}
        loadingRows={12}
      >
        {reports.data && (
          <div className="space-y-4">
            <Card>
              <CardHeader className="border-b py-4">
                <CardTitle className="text-sm">Revenue and profit trend</CardTitle>
                <p className="text-xs text-muted-foreground">Daily over {days} days</p>
              </CardHeader>
              <CardContent className="pt-4">
                <Chart
                  type="line"
                  height={300}
                  series={[
                    {
                      name: 'Revenue',
                      data: reports.data.revenueTrend.map((point) => ({ x: point.bucket, y: point.value })),
                    },
                    {
                      name: 'Gross profit',
                      data: reports.data.revenueTrend.map((point) => ({
                        x: point.bucket,
                        y: point.secondary,
                      })),
                    },
                  ]}
                  options={{
                    stroke: { curve: 'smooth', width: 2.5 },
                    xaxis: { type: 'datetime' },
                    yaxis: { labels: { formatter: (value) => compactMoney(value, currency) } },
                    tooltip: { x: { format: 'ddd dd MMM' }, y: { formatter: money } },
                    markers: { size: 0, hover: { size: 5 } },
                  }}
                />
              </CardContent>
            </Card>

            <div className="grid gap-4 lg:grid-cols-2">
              <BarReport
                title="Top products by revenue"
                subtitle={`Best sellers over ${days} days`}
                data={reports.data.topProductsByRevenue.slice(0, 12)}
                currency={currency}
              />
              <BarReport
                title="Top products by units"
                subtitle="Volume movers, which are not always the earners"
                data={reports.data.topProductsByVolume.slice(0, 12)}
                currency={currency}
                rawNumbers
              />
            </div>

            <div className="grid gap-4 lg:grid-cols-3">
              <DonutReport
                title="Revenue by category"
                data={reports.data.revenueByCategory}
                currency={currency}
              />
              <DonutReport
                title="Stock value by category"
                data={reports.data.stockValueByCategory}
                currency={currency}
              />
              <DonutReport
                title="Customer tiers"
                data={reports.data.customerTiers}
                currency={currency}
                rawNumbers
              />
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <BarReport
                title="Revenue by branch"
                subtitle="Branch comparison"
                data={reports.data.revenueByBranch}
                currency={currency}
              />
              <BarReport
                title="Revenue per cashier"
                subtitle="Employee productivity"
                data={reports.data.revenueByCashier}
                currency={currency}
              />
            </div>

            <div className="grid gap-4 lg:grid-cols-2">
              <DonutReport
                title="Stock movement by type"
                data={reports.data.stockMovementByType}
                currency={currency}
                rawNumbers
              />
              <DonutReport
                title="Attendance mix"
                data={reports.data.attendanceMix}
                currency={currency}
                rawNumbers
              />
            </div>

            {/* ABC analysis: the classic inventory-control technique. */}
            <Card>
              <CardHeader className="border-b py-4">
                <CardTitle className="text-sm">ABC inventory analysis</CardTitle>
                <p className="text-xs text-muted-foreground">
                  Class A is the top 80% of revenue and deserves tight control. Class C is the
                  long tail and does not.
                </p>
              </CardHeader>
              <CardContent className="p-0">
                <div className="max-h-[460px] overflow-y-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Class</TableHead>
                        <TableHead>Product</TableHead>
                        <TableHead className="text-right">Revenue</TableHead>
                        <TableHead className="text-right">Units</TableHead>
                        <TableHead className="text-right">Cumulative</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {reports.data.abcAnalysis.map((row) => (
                        <TableRow key={row.product}>
                          <TableCell>
                            <Badge
                              variant={
                                row.classification === 'A'
                                  ? 'success'
                                  : row.classification === 'B'
                                    ? 'warning'
                                    : 'muted'
                              }
                            >
                              {row.classification}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-xs font-medium">{row.product}</TableCell>
                          <TableCell className="numeric text-right text-xs">
                            {money(row.revenue)}
                          </TableCell>
                          <TableCell className="numeric text-right text-xs">
                            {number(row.units)}
                          </TableCell>
                          <TableCell className="numeric text-right text-xs text-muted-foreground">
                            {row.cumulativeShare.toFixed(1)}%
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </DataState>
    </>
  )
}

function BarReport({
  title,
  subtitle,
  data,
  currency,
  rawNumbers,
}: {
  title: string
  subtitle?: string
  data: Labelled[]
  currency: string
  rawNumbers?: boolean
}) {
  return (
    <Card>
      <CardHeader className="border-b py-4">
        <CardTitle className="text-sm">{title}</CardTitle>
        {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
      </CardHeader>
      <CardContent className="pt-4">
        {data.length === 0 ? (
          <p className="py-12 text-center text-xs text-muted-foreground">No data for this period</p>
        ) : (
          <Chart
            type="bar"
            height={Math.max(240, data.length * 26)}
            series={[{ name: title, data: data.map((entry) => Number(entry.value)) }]}
            options={{
              plotOptions: { bar: { horizontal: true, borderRadius: 4, barHeight: '68%' } },
              xaxis: {
                categories: data.map((entry) => entry.label),
                labels: {
                  formatter: (value) =>
                    rawNumbers ? number(Number(value)) : compactMoney(Number(value), currency),
                },
              },
              tooltip: {
                y: {
                  formatter: (value) =>
                    rawNumbers ? `${number(value)}` : moneyWhole(value, currency),
                },
              },
            }}
          />
        )}
      </CardContent>
    </Card>
  )
}

function DonutReport({
  title,
  data,
  currency,
  rawNumbers,
}: {
  title: string
  data: Labelled[]
  currency: string
  rawNumbers?: boolean
}) {
  const humanise = (value: string) =>
    value
      .toLowerCase()
      .split('_')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ')

  return (
    <Card>
      <CardHeader className="border-b py-4">
        <CardTitle className="text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent className="pt-4">
        {data.length === 0 ? (
          <p className="py-12 text-center text-xs text-muted-foreground">No data</p>
        ) : (
          <Chart
            type="donut"
            height={260}
            series={data.slice(0, 8).map((entry) => Number(entry.value))}
            options={{
              labels: data.slice(0, 8).map((entry) => humanise(entry.label)),
              legend: { position: 'bottom', fontSize: '11px' },
              colors: SERIES_COLOURS,
              plotOptions: { pie: { donut: { size: '60%' } } },
              tooltip: {
                y: {
                  formatter: (value) =>
                    rawNumbers ? `${number(value)}` : moneyWhole(value, currency),
                },
              },
            }}
          />
        )}
      </CardContent>
    </Card>
  )
}
