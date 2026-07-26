import { useState } from 'react'
import { ScrollText, ShieldCheck } from 'lucide-react'
import { dateTime } from '@/lib/format'
import { usePagedApi } from '@/hooks/useApi'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface AuditRow {
  id: number
  actorName: string
  actorRole?: string
  action: string
  module?: string
  entityType?: string
  targetName?: string
  details?: string
  ipAddress?: string
  occurredAt: string
}

interface LoginRow {
  id: number
  userName?: string
  attemptedEmail: string
  status: string
  ipAddress?: string
  device?: string
  failureReason?: string
  occurredAt: string
}

export default function AuditPage() {
  const [tab, setTab] = useState<'audit' | 'logins'>('audit')
  const audit = usePagedApi<AuditRow>('/audit')
  const logins = usePagedApi<LoginRow>('/audit/login-history')

  const auditColumns: Column<AuditRow>[] = [
    { key: 'when', header: 'When', render: (row) => dateTime(row.occurredAt) },
    {
      key: 'actor',
      header: 'Who',
      render: (row) => (
        <div>
          <p className="font-medium">{row.actorName}</p>
          <p className="text-[11px] text-muted-foreground">{row.actorRole ?? '—'}</p>
        </div>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      render: (row) => <Badge variant="secondary">{row.action}</Badge>,
    },
    {
      key: 'module',
      header: 'Module',
      render: (row) => <span className="text-muted-foreground">{row.module ?? '—'}</span>,
    },
    {
      key: 'target',
      header: 'Target',
      render: (row) => (
        <div>
          <p>{row.targetName ?? '—'}</p>
          <p className="text-[11px] text-muted-foreground">{row.entityType ?? ''}</p>
        </div>
      ),
    },
    {
      key: 'details',
      header: 'Details',
      render: (row) => (
        <span className="block max-w-[280px] truncate text-muted-foreground">
          {row.details ?? '—'}
        </span>
      ),
    },
    {
      key: 'ip',
      header: 'IP',
      render: (row) => <span className="font-mono text-muted-foreground">{row.ipAddress ?? '—'}</span>,
    },
  ]

  const loginColumns: Column<LoginRow>[] = [
    { key: 'when', header: 'When', render: (row) => dateTime(row.occurredAt) },
    {
      key: 'who',
      header: 'Account',
      render: (row) => (
        <div>
          <p className="font-medium">{row.userName ?? 'Unknown account'}</p>
          <p className="text-[11px] text-muted-foreground">{row.attemptedEmail}</p>
        </div>
      ),
    },
    { key: 'status', header: 'Result', render: (row) => <StatusBadge status={row.status} /> },
    {
      key: 'device',
      header: 'Device',
      render: (row) => <span className="text-muted-foreground">{row.device ?? '—'}</span>,
    },
    {
      key: 'ip',
      header: 'IP',
      render: (row) => <span className="font-mono text-muted-foreground">{row.ipAddress ?? '—'}</span>,
    },
    {
      key: 'reason',
      header: 'Reason',
      render: (row) => <span className="text-muted-foreground">{row.failureReason ?? '—'}</span>,
    },
  ]

  return (
    <>
      <PageHeader
        title="Audit Trail"
        subtitle="Every state-changing operation and every sign-in attempt, successful or not"
      />

      <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="audit">
            <ScrollText className="mr-1.5 h-3.5 w-3.5" /> Audit log
          </TabsTrigger>
          <TabsTrigger value="logins">
            <ShieldCheck className="mr-1.5 h-3.5 w-3.5" /> Sign-in history
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === 'audit' ? (
        <ResourceTable<AuditRow>
          title="Audit log"
          subtitle="Actor and target names are stored as snapshots, so entries stay readable after a record is renamed or removed"
          columns={auditColumns}
          rows={audit.rows}
          loading={audit.loading}
          error={audit.error}
          reload={audit.reload}
          search={audit.search}
          onSearchChange={audit.setSearch}
          searchPlaceholder="Actor, action or target"
          page={audit.page}
          totalPages={audit.totalPages}
          totalElements={audit.totalElements}
          onPageChange={audit.setPage}
          emptyTitle="No audit entries"
        />
      ) : (
        <ResourceTable<LoginRow>
          title="Sign-in history"
          subtitle="Failed attempts are recorded even when the email matches no account, so credential stuffing is visible"
          columns={loginColumns}
          rows={logins.rows}
          loading={logins.loading}
          error={logins.error}
          reload={logins.reload}
          page={logins.page}
          totalPages={logins.totalPages}
          totalElements={logins.totalElements}
          onPageChange={logins.setPage}
          emptyTitle="No sign-in attempts recorded"
        />
      )}
    </>
  )
}
