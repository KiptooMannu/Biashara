import { AlertCircle, Inbox, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'

export function LoadingRows({ rows = 5, className }: { rows?: number; className?: string }) {
  return (
    <div className={className ?? 'space-y-3 p-5'} role="status" aria-label="Loading">
      {Array.from({ length: rows }).map((_, index) => (
        <Skeleton key={index} className="h-4" style={{ width: `${96 - index * 11}%` }} />
      ))}
    </div>
  )
}

export function EmptyState({
  title,
  message,
  icon: Icon = Inbox,
  action,
}: {
  title: string
  message?: string
  icon?: LucideIcon
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-14 text-center">
      <div className="mb-3 rounded-full bg-muted p-3">
        <Icon className="h-6 w-6 text-muted-foreground" />
      </div>
      <p className="text-sm font-semibold">{title}</p>
      {message && <p className="mt-1 max-w-sm text-xs text-muted-foreground">{message}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-12 text-center">
      <div className="mb-3 rounded-full bg-destructive/10 p-3">
        <AlertCircle className="h-6 w-6 text-destructive" />
      </div>
      <p className="text-sm font-semibold">Could not load this</p>
      <p className="mt-1 max-w-md text-xs text-muted-foreground">{message}</p>
      {onRetry && (
        <Button variant="outline" size="sm" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

/**
 * Renders the right thing for the current request state.
 *
 * Having one component decide this is what stops half the screens from showing a
 * spinner forever and the other half from rendering an empty table as though the
 * business genuinely has no data.
 */
export function DataState({
  loading,
  error,
  empty,
  onRetry,
  emptyTitle = 'Nothing here yet',
  emptyMessage,
  loadingRows = 5,
  children,
}: {
  loading: boolean
  error: string | null
  empty: boolean
  onRetry?: () => void
  emptyTitle?: string
  emptyMessage?: string
  loadingRows?: number
  children: ReactNode
}) {
  if (loading) return <LoadingRows rows={loadingRows} />
  if (error) return <ErrorState message={error} onRetry={onRetry} />
  if (empty) return <EmptyState title={emptyTitle} message={emptyMessage} />
  return <>{children}</>
}
