import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { moneyCompact, number as formatNumber, percent, signedPercent } from '@/lib/format'
import type { KpiTile } from '@/lib/types'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * A single KPI.
 *
 * The delta colour is driven by the tile's own {@code higherIsBetter} flag rather
 * than by the sign of the change, because rising expenses are not good news while
 * rising revenue is. Getting that backwards is the classic dashboard bug.
 */
export function KpiCard({ tile, currency }: { tile: KpiTile; currency: string }) {
  const change = tile.changePercent
  const hasChange = change !== null && change !== undefined
  const improving = hasChange && (tile.higherIsBetter ? change > 0 : change < 0)
  const flat = hasChange && Math.abs(change) < 0.05

  const DeltaIcon = flat ? Minus : (change ?? 0) > 0 ? ArrowUpRight : ArrowDownRight

  return (
    <Card className="p-4">
      <p className="truncate text-xs font-medium text-muted-foreground">{tile.label}</p>

      <p className="numeric mt-1.5 text-xl font-bold tracking-tight">{formatValue(tile, currency)}</p>

      <div className="mt-1.5 flex min-h-[18px] items-center gap-1.5">
        {hasChange ? (
          <>
            <span
              className={cn(
                'inline-flex items-center gap-0.5 text-xs font-semibold',
                flat
                  ? 'text-muted-foreground'
                  : improving
                    ? 'text-emerald-600'
                    : 'text-red-600',
              )}
            >
              <DeltaIcon className="h-3.5 w-3.5" />
              {signedPercent(change)}
            </span>
            {tile.hint && (
              <span className="truncate text-[11px] text-muted-foreground">{tile.hint}</span>
            )}
          </>
        ) : (
          tile.hint && <span className="truncate text-[11px] text-muted-foreground">{tile.hint}</span>
        )}
      </div>
    </Card>
  )
}

function formatValue(tile: KpiTile, currency: string): string {
  if (tile.unit === 'KES') return moneyCompact(tile.value, currency)
  if (tile.unit === '%') return percent(tile.value)
  return `${formatNumber(tile.value)}${tile.unit && tile.unit !== 'items' ? '' : ''}`
}
