import { Link } from 'react-router-dom'
import { ArrowRight, Sparkles } from 'lucide-react'
import { moneyWhole, percent, timeAgo } from '@/lib/format'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import type { AiInsight, BusinessHealth } from '@/lib/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { Chart } from '@/components/charts/Chart'
import { cn } from '@/lib/utils'

/**
 * Business intelligence.
 *
 * Where the dashboard shows the numbers, this screen shows the reasoning: the full
 * insight set with cause and recommendation, and the health score broken into the
 * components that produced it.
 */
export default function AnalyticsPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const insights = useApi<AiInsight[]>('/ai/insights')
  const health = useApi<BusinessHealth>('/dashboard/health')

  const bySeverity = (severity: string) =>
    (insights.data ?? []).filter((insight) => insight.severity === severity).length

  return (
    <>
      <PageHeader
        title="Business Intelligence"
        subtitle="Every insight with its cause and recommended action, plus the health score broken down"
        action={
          <Button variant="outline" asChild>
            <Link to="/assistant">
              <Sparkles /> Ask the assistant
            </Link>
          </Button>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="flex-row items-center justify-between space-y-0 border-b py-4">
            <div>
              <CardTitle className="text-sm">All insights</CardTitle>
              <p className="text-xs text-muted-foreground">
                Generated from your transactions, not from templates
              </p>
            </div>
            <div className="flex gap-1.5">
              {bySeverity('CRITICAL') > 0 && (
                <Badge variant="danger">{bySeverity('CRITICAL')} critical</Badge>
              )}
              {bySeverity('WARNING') > 0 && (
                <Badge variant="warning">{bySeverity('WARNING')} warnings</Badge>
              )}
            </div>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={insights.loading}
              error={insights.error}
              empty={(insights.data?.length ?? 0) === 0}
              onRetry={insights.reload}
              emptyTitle="No insights yet"
              emptyMessage="Insights appear once there is enough trading history to reason about."
            >
              <div className="divide-y">
                {(insights.data ?? []).map((insight) => (
                  <div
                    key={insight.id}
                    className={cn(
                      'border-l-4 p-4',
                      insight.severity === 'CRITICAL' && 'border-l-red-500',
                      insight.severity === 'WARNING' && 'border-l-amber-500',
                      insight.severity === 'SUCCESS' && 'border-l-emerald-500',
                      insight.severity === 'INFO' && 'border-l-sky-500',
                    )}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <p className="text-sm font-semibold">{insight.title}</p>
                      <div className="flex shrink-0 flex-wrap items-center gap-1.5">
                        <Badge variant="secondary">{insight.type.replace(/_/g, ' ')}</Badge>
                        {insight.module && <Badge variant="muted">{insight.module}</Badge>}
                      </div>
                    </div>

                    {insight.summary && (
                      <p className="numeric mt-1 text-xs text-muted-foreground">{insight.summary}</p>
                    )}

                    <div className="mt-2.5 grid gap-2 sm:grid-cols-2">
                      {insight.cause && (
                        <div className="rounded-lg bg-muted/60 p-2.5">
                          <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                            Why it happened
                          </p>
                          <p className="mt-0.5 text-xs">{insight.cause}</p>
                        </div>
                      )}
                      {insight.recommendation && (
                        <div className="rounded-lg bg-primary/5 p-2.5">
                          <p className="text-[10px] font-semibold uppercase tracking-wider text-primary">
                            What to do
                          </p>
                          <p className="mt-0.5 text-xs">{insight.recommendation}</p>
                        </div>
                      )}
                    </div>

                    <div className="mt-2.5 flex flex-wrap items-center gap-3">
                      {insight.metricLabel && insight.metricValue != null && (
                        <div>
                          <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                            {insight.metricLabel}
                          </p>
                          <p className="numeric text-sm font-bold">
                            {insight.metricUnit === 'KES'
                              ? moneyWhole(insight.metricValue, currency)
                              : `${insight.metricValue}${insight.metricUnit === '%' ? '%' : ` ${insight.metricUnit ?? ''}`}`}
                          </p>
                        </div>
                      )}
                      {insight.confidence != null && (
                        <div>
                          <p className="text-[10px] uppercase tracking-wider text-muted-foreground">
                            Confidence
                          </p>
                          <p className="numeric text-sm font-bold">
                            {percent(insight.confidence, 0)}
                          </p>
                        </div>
                      )}
                      <div className="ml-auto flex items-center gap-2">
                        <span className="text-[11px] text-muted-foreground">
                          {timeAgo(insight.generatedAt)}
                        </span>
                        {insight.actionUrl && (
                          <Button variant="ghost" size="sm" asChild>
                            <Link to={insight.actionUrl}>
                              {insight.actionLabel ?? 'Open'} <ArrowRight />
                            </Link>
                          </Button>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </DataState>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Business health index</CardTitle>
            <p className="text-xs text-muted-foreground">Weighted from six live measures</p>
          </CardHeader>
          <CardContent className="pt-4">
            <DataState
              loading={health.loading}
              error={health.error}
              empty={!health.data}
              onRetry={health.reload}
            >
              {health.data && (
                <>
                  <Chart
                    type="radialBar"
                    height={200}
                    series={[health.data.score]}
                    options={{
                      plotOptions: {
                        radialBar: {
                          hollow: { size: '60%' },
                          dataLabels: {
                            name: { fontSize: '11px', offsetY: 22, color: '#64748b' },
                            value: {
                              fontSize: '28px',
                              fontWeight: 700,
                              offsetY: -12,
                              formatter: (value) => `${Math.round(Number(value))}`,
                            },
                          },
                        },
                      },
                      labels: [health.data.grade],
                      colors: [
                        health.data.score >= 85
                          ? '#059669'
                          : health.data.score >= 70
                            ? '#65a30d'
                            : health.data.score >= 55
                              ? '#f59e0b'
                              : '#dc2626',
                      ],
                    }}
                  />

                  <div className="mt-2 space-y-3">
                    {health.data.components.map((component) => (
                      <div key={component.name}>
                        <div className="flex items-baseline justify-between gap-2">
                          <p className="truncate text-xs font-medium">{component.name}</p>
                          <span className="numeric shrink-0 text-xs font-semibold">
                            {Math.round(component.score)}
                            <span className="ml-1 font-normal text-muted-foreground">
                              ×{Math.round(component.weight)}%
                            </span>
                          </span>
                        </div>
                        <Progress value={component.score} className="mt-1 h-1.5" />
                        <p className="mt-0.5 text-[11px] text-muted-foreground">{component.detail}</p>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </DataState>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
