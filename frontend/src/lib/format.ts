/** Display formatting. Kenyan shilling conventions throughout. */

const compactFormatter = new Intl.NumberFormat('en-KE', {
  notation: 'compact',
  maximumFractionDigits: 1,
})

const wholeFormatter = new Intl.NumberFormat('en-KE', { maximumFractionDigits: 0 })

const decimalFormatter = new Intl.NumberFormat('en-KE', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** Full precision: use where an exact figure matters, such as a receipt line. */
export function money(value: number | null | undefined, currency = 'KES'): string {
  if (value == null) return '—'
  return `${currency} ${decimalFormatter.format(value)}`
}

/** Rounded to the shilling: use in tables and totals. */
export function moneyWhole(value: number | null | undefined, currency = 'KES'): string {
  if (value == null) return '—'
  return `${currency} ${wholeFormatter.format(value)}`
}

/**
 * Abbreviated: use on KPI tiles and chart axes, where 1.8M reads faster than
 * 1,820,450 and the exact figure is available on hover.
 */
export function moneyCompact(value: number | null | undefined, currency = 'KES'): string {
  if (value == null) return '—'
  return `${currency} ${compactFormatter.format(value)}`
}

export function number(value: number | null | undefined): string {
  if (value == null) return '—'
  return wholeFormatter.format(value)
}

export function percent(value: number | null | undefined, digits = 1): string {
  if (value == null) return '—'
  return `${value.toFixed(digits)}%`
}

export function signedPercent(value: number | null | undefined, digits = 1): string {
  if (value == null) return '—'
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(digits)}%`
}

export function dateShort(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleDateString('en-KE', { day: '2-digit', month: 'short', year: 'numeric' })
}

export function dateTime(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value
  return parsed.toLocaleString('en-KE', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function timeAgo(value: string | null | undefined): string {
  if (!value) return '—'
  const parsed = new Date(value).getTime()
  if (Number.isNaN(parsed)) return value
  const seconds = Math.floor((Date.now() - parsed) / 1000)
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}d ago`
  return dateShort(value)
}

/** Turns SCREAMING_SNAKE_CASE enum values into readable labels. */
export function humanise(value: string | null | undefined): string {
  if (!value) return '—'
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

export function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/)
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
}
