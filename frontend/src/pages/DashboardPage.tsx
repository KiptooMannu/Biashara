import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  AlertTriangle,
  ArrowRight,
  Boxes,
  ChevronRight,
  Lightbulb,
  RefreshCw,
  Sparkles,
  TrendingUp,
} from 'lucide-react'
import { api, errorMessage } from '@/lib/api'
import { dateTime, moneyCompact, moneyWhole, number, percent, timeAgo } from '@/lib/format'
import { useAuth } from '@/store/auth'
import type { DashboardResponse } from '@/lib/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState, LoadingRows } from '@/components/shared/DataState'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { KpiCard } from '@/components/dashboard/KpiCard'
import { Chart, SERIES_COLOURS, compactMoney } from '@/components/charts/Chart'
import { cn } from '@/lib/utils'

export default function DashboardPage() {
  const user = useAuth((state) => state.user)
  const [data, setData] = useState<DashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const { data: response } = await api.get<DashboardResponse>('/dashboard')
      setData(response)
    } catch (caught) {
      setError(errorMessage(caught, 'Could not load the dashboard'))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  if (loading) {
    return (
      <div className="space-y-4">
        <PageHeader title="Dashboard" subtitle="Loading your business…" />
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
          {Array.from({ length: 7 }).map((_, index) => (
            <Card key={index} className="p-4">
              <LoadingRows rows={2} className="space-y-2" />
            </Card>
          ))}
        </div>
        <Card>
          <LoadingRows rows={8} />
        </Card>
      </div>
    )
  }

  if (error || !data) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <Card>
          <ErrorState message={error ?? 'No data returned'} onRetry={load} />
        </Card>
      </>
    )
  }

  const currency = data.currency ?? 'KES'

  return (
    <>
      <PageHeader
        title={`Good ${greeting()}, ${user?.fullName?.split(' ')[0]}`}
        subtitle={`${data.businessName} · updated ${timeAgo(data.generatedAt)}`}
        action={
          <Button variant="outline" size="sm" onClick={load}>
            <RefreshCw /> Refresh
          </Button>
        }
      />

      {/* KPI row */}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4 xl:grid-cols-7">
        {data.kpis.map((tile) => (
          <KpiCard key={tile.key} tile={tile} currency={currency} />
        ))}
      </div>

      {/* AI insights: the differentiator, so it sits directly under the numbers. */}
      {data.insights.length > 0 && (
        <Card className="mt-4 overflow-hidden">
          <CardHeader className="flex-row items-center justify-between space-y-0 border-b py-4">
            <div className="flex items-center gap-2">
              <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10">
                <Sparkles className="h-4 w-4 text-primary" />
              </div>
              <div>
                <CardTitle className="text-sm">AI insights</CardTitle>
                <p className="text-xs text-muted-foreground">
                  What happened, why, and what to do — derived from your data
                </p>
              </div>
            </div>
            <Badge variant="muted">{data.insights.length}</Badge>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y">
              {data.insights.slice(0, 5).map((insight) => (
                <div
                  key={insight.id}
                  className={cn(
                    'border-l-4 p-4 transition-colors hover:bg-muted/40',
                    insight.severity === 'CRITICAL' && 'border-l-red-500',
                    insight.severity === 'WARNING' && 'border-l-amber-500',
                    insight.severity === 'SUCCESS' && 'border-l-emerald-500',
                    insight.severity === 'INFO' && 'border-l-sky-500',
                  )}
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <p className="text-sm font-semibold">{insight.title}</p>
                    <div className="flex shrink-0 items-center gap-1.5">
                      {insight.module && <Badge variant="secondary">{insight.module}</Badge>}
                      {insight.confidence != null && (
                        <Badge variant="muted">{percent(insight.confidence, 0)} confident</Badge>
                      )}
                    </div>
                  </div>

                  {insight.summary && (
                    <p className="numeric mt-1 text-xs text-muted-foreground">{insight.summary}</p>
                  )}

                  <div className="mt-2.5 grid gap-2 sm:grid-cols-2">
                    {insight.cause && (
                      <div className="rounded-lg bg-muted/60 p-2.5">
                        <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                          Why
                        </p>
                        <p className="mt-0.5 text-xs">{insight.cause}</p>
                      </div>
                    )}
                    {insight.recommendation && (
                      <div className="rounded-lg bg-primary/5 p-2.5">
                        <p className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wider text-primary">
                          <Lightbulb className="h-3 w-3" /> Do this
                        </p>
                        <p className="mt-0.5 text-xs">{insight.recommendation}</p>
                      </div>
                    )}
                  </div>

                  {insight.actionUrl && (
                    <Link
                      to={insight.actionUrl}
                      className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                    >
                      {insight.actionLabel ?? 'Open'} <ChevronRight className="h-3 w-3" />
                    </Link>
                  )}
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Revenue and profit trend */}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Revenue and gross profit</CardTitle>
            <p className="text-xs text-muted-foreground">Daily, last 90 days</p>
          </CardHeader>
          <CardContent className="pt-4">
            <Chart
              type="area"
              height={300}
              series={[
                {
                  name: 'Revenue',
                  data: data.revenueSeries.map((point) => ({ x: point.bucket, y: point.value })),
                },
                {
                  name: 'Gross profit',
                  data: data.revenueSeries.map((point) => ({ x: point.bucket, y: point.secondary })),
                },
              ]}
              options={{
                stroke: { curve: 'smooth', width: 2 },
                fill: {
                  type: 'gradient',
                  gradient: { shadeIntensity: 1, opacityFrom: 0.35, opacityTo: 0.02, stops: [0, 95] },
                },
                xaxis: { type: 'datetime' },
                yaxis: { labels: { formatter: (value) => compactMoney(value, currency) } },
                tooltip: { x: { format: 'ddd dd MMM' }, y: { formatter: (value) => moneyWhole(value, currency) } },
              }}
            />
          </CardContent>
        </Card>

        {/* Business health index */}
        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Business health</CardTitle>
            <p className="text-xs text-muted-foreground">Six weighted components</p>
          </CardHeader>
          <CardContent className="pt-4">
            <div className="flex items-center gap-4">
              <div className="relative">
                <Chart
                  type="radialBar"
                  height={150}
                  series={[data.health.score]}
                  options={{
                    chart: { sparkline: { enabled: true } },
                    plotOptions: {
                      radialBar: {
                        hollow: { size: '58%' },
                        dataLabels: {
                          name: { show: false },
                          value: {
                            fontSize: '22px',
                            fontWeight: 700,
                            offsetY: 8,
                            formatter: (value) => `${Math.round(Number(value))}`,
                          },
                        },
                      },
                    },
                    colors: [healthColour(data.health.score)],
                  }}
                />
              </div>
              <div className="min-w-0">
                <p className="text-lg font-bold">{data.health.grade}</p>
                <p className="text-xs text-muted-foreground">
                  Scored out of 100 from live figures
                </p>
              </div>
            </div>

            <div className="mt-3 space-y-2.5">
              {data.health.components.map((component) => (
                <div key={component.name}>
                  <div className="flex items-baseline justify-between gap-2">
                    <p className="truncate text-xs font-medium">{component.name}</p>
                    <span className="numeric shrink-0 text-xs font-semibold">
                      {Math.round(component.score)}
                    </span>
                  </div>
                  <Progress value={component.score} className="mt-1 h-1.5" />
                  <p className="mt-0.5 truncate text-[11px] text-muted-foreground">
                    {component.detail}
                  </p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Distribution charts */}
      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <ChartCard title="Revenue by category" subtitle="Last 30 days">
          {data.revenueByCategory.length === 0 ? (
            <NoChartData />
          ) : (
            <Chart
              type="donut"
              height={260}
              series={data.revenueByCategory.slice(0, 8).map((entry) => Number(entry.value))}
              options={{
                labels: data.revenueByCategory.slice(0, 8).map((entry) => entry.label),
                legend: { position: 'bottom', fontSize: '11px' },
                plotOptions: {
                  pie: {
                    donut: {
                      size: '62%',
                      labels: {
                        show: true,
                        total: {
                          show: true,
                          label: 'Total',
                          fontSize: '11px',
                          formatter: (chart) =>
                            compactMoney(
                              chart.globals.seriesTotals.reduce((sum: number, value: number) => sum + value, 0),
                              currency,
                            ),
                        },
                      },
                    },
                  },
                },
                tooltip: { y: { formatter: (value) => moneyWhole(value, currency) } },
              }}
            />
          )}
        </ChartCard>

        <ChartCard title="Revenue by branch" subtitle="Last 30 days">
          {data.revenueByBranch.length === 0 ? (
            <NoChartData />
          ) : (
            <Chart
              type="bar"
              height={260}
              series={[
                {
                  name: 'Revenue',
                  data: data.revenueByBranch.map((entry) => Number(entry.value)),
                },
              ]}
              options={{
                plotOptions: { bar: { horizontal: true, borderRadius: 4, barHeight: '65%' } },
                xaxis: {
                  categories: data.revenueByBranch.map((entry) => entry.label),
                  labels: { formatter: (value) => compactMoney(Number(value), currency) },
                },
                tooltip: { y: { formatter: (value) => moneyWhole(value, currency) } },
              }}
            />
          )}
        </ChartCard>

        <ChartCard title="How customers pay" subtitle="Last 30 days">
          {data.revenueByPaymentMethod.length === 0 ? (
            <NoChartData />
          ) : (
            <Chart
              type="pie"
              height={260}
              series={data.revenueByPaymentMethod.map((entry) => Number(entry.value))}
              options={{
                labels: data.revenueByPaymentMethod.map((entry) => humanLabel(entry.label)),
                legend: { position: 'bottom', fontSize: '11px' },
                tooltip: { y: { formatter: (value) => moneyWhole(value, currency) } },
              }}
            />
          )}
        </ChartCard>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <ChartCard title="Busiest hours" subtitle="Revenue by hour of day, last 30 days">
          {data.salesByHour.length === 0 ? (
            <NoChartData />
          ) : (
            <Chart
              type="bar"
              height={250}
              series={[
                { name: 'Revenue', data: data.salesByHour.map((entry) => Number(entry.value)) },
              ]}
              options={{
                plotOptions: { bar: { borderRadius: 3, columnWidth: '60%' } },
                xaxis: { categories: data.salesByHour.map((entry) => `${entry.label}:00`) },
                yaxis: { labels: { formatter: (value) => compactMoney(value, currency) } },
                tooltip: { y: { formatter: (value) => moneyWhole(value, currency) } },
                colors: [SERIES_COLOURS[1]],
              }}
            />
          )}
        </ChartCard>

        <ChartCard title="Stock movement" subtitle="Units in versus out, last 30 days">
          {data.inventoryMovement.length === 0 ? (
            <NoChartData />
          ) : (
            <Chart
              type="bar"
              height={250}
              series={[
                {
                  name: 'Stock in',
                  data: data.inventoryMovement.map((point) => ({ x: point.bucket, y: point.value })),
                },
                {
                  name: 'Stock out',
                  data: data.inventoryMovement.map((point) => ({
                    x: point.bucket,
                    y: -Number(point.secondary),
                  })),
                },
              ]}
              options={{
                chart: { stacked: true },
                plotOptions: { bar: { borderRadius: 2, columnWidth: '70%' } },
                xaxis: { type: 'datetime' },
                yaxis: { labels: { formatter: (value) => `${Math.abs(Math.round(value))}` } },
                tooltip: {
                  x: { format: 'ddd dd MMM' },
                  y: { formatter: (value) => `${Math.abs(Math.round(value))} units` },
                },
                colors: [SERIES_COLOURS[0], SERIES_COLOURS[4]],
              }}
            />
          )}
        </ChartCard>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <ChartCard title="Expense breakdown" subtitle="Last 90 days">
          {data.expenseBreakdown.length === 0 ? (
            <NoChartData />
          ) : (
            <Chart
              type="bar"
              height={280}
              series={[
                {
                  name: 'Spend',
                  data: data.expenseBreakdown.slice(0, 10).map((entry) => Number(entry.value)),
                },
              ]}
              options={{
                plotOptions: { bar: { horizontal: true, borderRadius: 4, barHeight: '65%', distributed: true } },
                xaxis: {
                  categories: data.expenseBreakdown.slice(0, 10).map((entry) => entry.label),
                  labels: { formatter: (value) => compactMoney(Number(value), currency) },
                },
                legend: { show: false },
                tooltip: { y: { formatter: (value) => moneyWhole(value, currency) } },
              }}
            />
          )}
        </ChartCard>

        <ChartCard title="Top products" subtitle="By revenue, last 30 days">
          {data.topProducts.length === 0 ? (
            <NoChartData />
          ) : (
            <div className="space-y-2.5 pt-1">
              {data.topProducts.slice(0, 8).map((entry, index) => {
                const max = Number(data.topProducts[0].value) || 1
                const share = (Number(entry.value) / max) * 100
                return (
                  <div key={entry.label}>
                    <div className="flex items-baseline justify-between gap-2">
                      <p className="truncate text-xs font-medium">
                        <span className="mr-1.5 text-muted-foreground">{index + 1}.</span>
                        {entry.label}
                      </p>
                      <span className="numeric shrink-0 text-xs font-semibold">
                        {moneyCompact(Number(entry.value), currency)}
                      </span>
                    </div>
                    <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-muted">
                      <div
                        className="h-full rounded-full bg-primary"
                        style={{ width: `${share}%` }}
                      />
                    </div>
                    <p className="mt-0.5 text-[11px] text-muted-foreground">
                      {number(entry.count)} units sold
                    </p>
                  </div>
                )
              })}
            </div>
          )}
        </ChartCard>
      </div>

      {/* Operational tables */}
      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0 border-b py-4">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-amber-500" />
              <CardTitle className="text-sm">Needs reordering</CardTitle>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/inventory">
                Inventory <ArrowRight />
              </Link>
            </Button>
          </CardHeader>
          <CardContent className="p-0">
            {data.lowStock.length === 0 ? (
              <p className="px-5 py-8 text-center text-xs text-muted-foreground">
                Every line is above its minimum level.
              </p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Product</TableHead>
                    <TableHead className="text-right">In stock</TableHead>
                    <TableHead className="text-right">Minimum</TableHead>
                    <TableHead className="text-right">Days left</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.lowStock.map((product) => (
                    <TableRow key={product.id}>
                      <TableCell>
                        <p className="text-xs font-medium">{product.name}</p>
                        <p className="text-[11px] text-muted-foreground">{product.sku}</p>
                      </TableCell>
                      <TableCell className="numeric text-right text-xs">
                        <span className={product.outOfStock ? 'font-bold text-red-600' : 'font-semibold'}>
                          {product.currentStock}
                        </span>
                      </TableCell>
                      <TableCell className="numeric text-right text-xs text-muted-foreground">
                        {product.minStock}
                      </TableCell>
                      <TableCell className="numeric text-right text-xs">
                        {product.daysUntilStockout != null ? (
                          <Badge variant={product.daysUntilStockout <= 3 ? 'danger' : 'warning'}>
                            {product.daysUntilStockout}d
                          </Badge>
                        ) : (
                          <span className="text-muted-foreground">—</span>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0 border-b py-4">
            <div className="flex items-center gap-2">
              <TrendingUp className="h-4 w-4 text-primary" />
              <CardTitle className="text-sm">Latest sales</CardTitle>
            </div>
            <Button variant="ghost" size="sm" asChild>
              <Link to="/sales">
                All sales <ArrowRight />
              </Link>
            </Button>
          </CardHeader>
          <CardContent className="p-0">
            {data.recentSales.length === 0 ? (
              <p className="px-5 py-8 text-center text-xs text-muted-foreground">No sales yet.</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Invoice</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Paid by</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.recentSales.map((sale) => (
                    <TableRow key={sale.id}>
                      <TableCell>
                        <p className="font-mono text-xs font-medium">{sale.invoiceNumber}</p>
                        <p className="text-[11px] text-muted-foreground">{dateTime(sale.saleDate)}</p>
                      </TableCell>
                      <TableCell className="max-w-[120px] truncate text-xs">
                        {sale.customerName}
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={sale.paymentMethod} />
                      </TableCell>
                      <TableCell className="numeric text-right text-xs font-semibold">
                        {moneyWhole(sale.total, currency)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>
    </>
  )
}

function ChartCard({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: string
  children: React.ReactNode
}) {
  return (
    <Card>
      <CardHeader className="border-b py-4">
        <CardTitle className="text-sm">{title}</CardTitle>
        {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
      </CardHeader>
      <CardContent className="pt-4">{children}</CardContent>
    </Card>
  )
}

function NoChartData() {
  return (
    <div className="flex h-[240px] flex-col items-center justify-center gap-2 text-center">
      <Boxes className="h-6 w-6 text-muted-foreground/50" />
      <p className="text-xs text-muted-foreground">Not enough data for this chart yet</p>
    </div>
  )
}

function healthColour(score: number): string {
  if (score >= 85) return '#059669'
  if (score >= 70) return '#65a30d'
  if (score >= 55) return '#f59e0b'
  if (score >= 40) return '#f97316'
  return '#dc2626'
}

function humanLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

function greeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return 'morning'
  if (hour < 17) return 'afternoon'
  return 'evening'
}
