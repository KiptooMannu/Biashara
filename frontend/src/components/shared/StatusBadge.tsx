import { Badge, type BadgeVariant } from '@/components/ui/badge'
import { humanise } from '@/lib/format'

/**
 * Maps backend status and severity values onto a consistent colour.
 *
 * Centralised so PAID reads the same green everywhere it appears, rather than
 * each screen picking its own.
 */
const SUCCESS = new Set([
  'PAID', 'COMPLETED', 'RECEIVED', 'APPROVED', 'ACTIVE', 'PRESENT', 'DONE',
  'SUCCESS', 'IN_USE', 'REIMBURSED', 'VIP',
])

const WARNING = new Set([
  'PENDING', 'PARTIAL', 'SENT', 'ORDERED', 'IN_PROGRESS', 'LATE', 'PLANNING',
  'WARNING', 'PARTIALLY_RECEIVED', 'PENDING_APPROVAL', 'IN_REVIEW', 'HALF_DAY',
  'PENDING_INVITATION', 'UNDER_MAINTENANCE', 'ON_LEAVE', 'GOLD',
])

const DANGER = new Set([
  'OVERDUE', 'CANCELLED', 'REJECTED', 'VOID', 'ABSENT', 'SUSPENDED', 'FAULTY',
  'CRITICAL', 'BLOCKED', 'LOST', 'FAILED', 'UNPAID', 'REFUNDED',
])

const MUTED = new Set([
  'DRAFT', 'INACTIVE', 'DORMANT', 'ON_HOLD', 'IN_STORAGE', 'DISPOSED',
  'HOLIDAY', 'BRONZE',
])

export function toneForStatus(status: string | null | undefined): BadgeVariant {
  if (!status) return 'muted'
  const value = status.toUpperCase()
  if (SUCCESS.has(value)) return 'success'
  if (WARNING.has(value)) return 'warning'
  if (DANGER.has(value)) return 'danger'
  if (MUTED.has(value)) return 'muted'
  return 'info'
}

export function StatusBadge({
  status,
  label,
}: {
  status: string | null | undefined
  label?: string
}) {
  if (!status) return <span className="text-muted-foreground">—</span>
  return <Badge variant={toneForStatus(status)}>{label ?? humanise(status)}</Badge>
}
