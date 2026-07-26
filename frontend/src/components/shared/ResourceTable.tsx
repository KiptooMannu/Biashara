import { ChevronLeft, ChevronRight, Search } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { DataState } from './DataState'
import { cn } from '@/lib/utils'

export interface Column<T> {
  key: string
  header: string
  /** Right-align numeric columns so figures line up down the page. */
  numeric?: boolean
  className?: string
  render: (row: T) => ReactNode
}

/**
 * A searchable, paged table.
 *
 * Every list screen in the application uses this, so pagination, empty states and
 * loading behaviour are identical everywhere rather than reimplemented per module.
 */
export function ResourceTable<T extends { id: number | string }>({
  title,
  subtitle,
  columns,
  rows,
  loading,
  error,
  reload,
  search,
  onSearchChange,
  searchPlaceholder = 'Search…',
  page,
  totalPages,
  totalElements,
  onPageChange,
  emptyTitle = 'Nothing to show',
  emptyMessage,
  action,
  onRowClick,
}: {
  title: string
  subtitle?: string
  columns: Column<T>[]
  rows: T[]
  loading: boolean
  error: string | null
  reload: () => void
  search?: string
  onSearchChange?: (value: string) => void
  searchPlaceholder?: string
  page?: number
  totalPages?: number
  totalElements?: number
  onPageChange?: (page: number) => void
  emptyTitle?: string
  emptyMessage?: string
  action?: ReactNode
  onRowClick?: (row: T) => void
}) {
  const showPager = page !== undefined && totalPages !== undefined && totalPages > 1

  return (
    <Card>
      <CardHeader className="flex-row flex-wrap items-center justify-between gap-3 space-y-0 border-b py-4">
        <div className="min-w-0">
          <CardTitle className="text-sm">{title}</CardTitle>
          <p className="text-xs text-muted-foreground">
            {subtitle ?? (totalElements !== undefined ? `${totalElements} record(s)` : undefined)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {onSearchChange && (
            <div className="relative">
              <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search ?? ''}
                onChange={(event) => onSearchChange(event.target.value)}
                placeholder={searchPlaceholder}
                className="h-9 w-48 pl-8 text-xs"
              />
            </div>
          )}
          {action}
        </div>
      </CardHeader>

      <CardContent className="p-0">
        <DataState
          loading={loading}
          error={error}
          empty={rows.length === 0}
          onRetry={reload}
          emptyTitle={emptyTitle}
          emptyMessage={emptyMessage}
          loadingRows={8}
        >
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  {columns.map((column) => (
                    <TableHead
                      key={column.key}
                      className={cn(column.numeric && 'text-right', column.className)}
                    >
                      {column.header}
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow
                    key={row.id}
                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                    className={onRowClick ? 'cursor-pointer' : undefined}
                  >
                    {columns.map((column) => (
                      <TableCell
                        key={column.key}
                        className={cn(
                          'text-xs',
                          column.numeric && 'numeric text-right',
                          column.className,
                        )}
                      >
                        {column.render(row)}
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {showPager && (
            <div className="flex items-center justify-between border-t px-4 py-3">
              <p className="text-xs text-muted-foreground">
                Page {page + 1} of {totalPages}
                {totalElements !== undefined && ` · ${totalElements} record(s)`}
              </p>
              <div className="flex gap-1.5">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => onPageChange?.(page - 1)}
                >
                  <ChevronLeft /> Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page >= totalPages - 1}
                  onClick={() => onPageChange?.(page + 1)}
                >
                  Next <ChevronRight />
                </Button>
              </div>
            </div>
          )}
        </DataState>
      </CardContent>
    </Card>
  )
}

/** Compact stat strip used above module tables. */
export function StatStrip({
  stats,
}: {
  stats: { label: string; value: string; tone?: 'default' | 'warning' | 'danger' | 'success' }[]
}) {
  return (
    <div className="mb-4 grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-6">
      {stats.map((stat) => (
        <Card key={stat.label} className="p-3.5">
          <p className="truncate text-[11px] font-medium text-muted-foreground">{stat.label}</p>
          <p
            className={cn(
              'numeric mt-1 text-lg font-bold',
              stat.tone === 'warning' && 'text-amber-600',
              stat.tone === 'danger' && 'text-red-600',
              stat.tone === 'success' && 'text-emerald-600',
            )}
          >
            {stat.value}
          </p>
        </Card>
      ))}
    </div>
  )
}
