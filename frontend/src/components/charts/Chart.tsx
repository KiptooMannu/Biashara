import ReactApexChart from 'react-apexcharts'
import type { ApexOptions } from 'apexcharts'
import { useMemo } from 'react'

/**
 * Shared ApexCharts configuration.
 *
 * One place decides typography, grid weight, tooltip style and the categorical
 * palette, so every chart in the application reads as part of the same system
 * rather than as a collection of library defaults.
 */

/** Categorical palette. Ordered so adjacent series stay distinguishable. */
export const SERIES_COLOURS = [
  '#059669', // brand green
  '#2563eb', // blue
  '#f59e0b', // amber
  '#7c3aed', // violet
  '#e11d48', // rose
  '#0891b2', // cyan
  '#65a30d', // lime
  '#c2410c', // orange
  '#4f46e5', // indigo
  '#be185d', // pink
]

const BASE: ApexOptions = {
  chart: {
    fontFamily: 'Inter, system-ui, sans-serif',
    toolbar: { show: false },
    zoom: { enabled: false },
    animations: { enabled: true, speed: 400 },
    parentHeightOffset: 0,
  },
  colors: SERIES_COLOURS,
  grid: {
    borderColor: 'rgba(148, 163, 184, 0.2)',
    strokeDashArray: 4,
    padding: { top: 0, right: 8, bottom: 0, left: 8 },
  },
  dataLabels: { enabled: false },
  legend: {
    fontSize: '12px',
    markers: { size: 6 },
    itemMargin: { horizontal: 8, vertical: 4 },
  },
  tooltip: {
    style: { fontSize: '12px' },
    theme: 'light',
  },
  xaxis: {
    labels: { style: { fontSize: '11px', colors: '#64748b' } },
    axisBorder: { show: false },
    axisTicks: { show: false },
  },
  yaxis: {
    labels: { style: { fontSize: '11px', colors: '#64748b' } },
  },
  states: {
    hover: { filter: { type: 'lighten' } },
  },
}

/** Deep merge, so callers override only what they need. */
function merge(base: ApexOptions, override: ApexOptions): ApexOptions {
  const result: Record<string, unknown> = { ...base }
  for (const [key, value] of Object.entries(override)) {
    const existing = result[key]
    if (
      value &&
      typeof value === 'object' &&
      !Array.isArray(value) &&
      existing &&
      typeof existing === 'object' &&
      !Array.isArray(existing)
    ) {
      result[key] = merge(existing as ApexOptions, value as ApexOptions)
    } else {
      result[key] = value
    }
  }
  return result as ApexOptions
}

export function Chart({
  type,
  series,
  options,
  height = 280,
}: {
  type: 'line' | 'area' | 'bar' | 'donut' | 'pie' | 'radialBar' | 'heatmap'
  series: ApexOptions['series']
  options?: ApexOptions
  height?: number
}) {
  const merged = useMemo(() => merge(BASE, options ?? {}), [options])

  return (
    <ReactApexChart
      type={type}
      series={series as never}
      options={merged}
      height={height}
      width="100%"
    />
  )
}

/** Compact currency formatter for axes and tooltips. */
export function compactMoney(value: number, currency = 'KES'): string {
  if (Math.abs(value) >= 1_000_000) return `${currency} ${(value / 1_000_000).toFixed(1)}M`
  if (Math.abs(value) >= 1_000) return `${currency} ${(value / 1_000).toFixed(0)}K`
  return `${currency} ${value.toFixed(0)}`
}
