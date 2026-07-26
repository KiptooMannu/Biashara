import { useState } from 'react'
import { Copy, KeyRound, Loader2, Mail, ShieldCheck, Unlock, UserPlus } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { dateShort, number, timeAgo } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { DataState } from '@/components/shared/DataState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface UserRow {
  id: number
  email: string
  fullName: string
  initials: string
  phone?: string
  employeeNumber?: string
  position?: string
  department?: string
  branch?: string
  roles: string[]
  primaryRoleName?: string
  hierarchyLevel: number
  permissionCount: number
  status: string
  firstLogin: boolean
  locked: boolean
  failedLoginAttempts: number
  lastLoginAt?: string
  lastLoginIp?: string
}

interface RoleOption {
  id: number
  code: string
  name: string
  description?: string
  hierarchyLevel: number
  permissionCount: number
}

interface Invitation {
  id: number
  userName: string
  email: string
  status: string
  temporaryPassword?: string
  invitedBy?: string
  expiresAt: string
  acceptedAt?: string
  expired: boolean
  emailBody?: string
}

interface Lookup {
  id: number
  name: string
  code?: string
}

export default function UsersPage() {
  const { can } = useAuth()
  const [tab, setTab] = useState<'users' | 'invitations' | 'roles'>('users')
  const [createOpen, setCreateOpen] = useState(false)
  const [created, setCreated] = useState<{ password: string; email: string; body: string } | null>(null)

  const paged = usePagedApi<UserRow>('/admin/users')
  const roles = useApi<RoleOption[]>('/admin/roles')
  const assignable = useApi<RoleOption[]>('/admin/roles/assignable', undefined, can('admin.user.create'))
  const invitations = useApi<Invitation[]>('/admin/invitations')
  const departments = useApi<Lookup[]>('/admin/departments')
  const branches = useApi<Lookup[]>('/admin/branches')

  async function resetPassword(user: UserRow) {
    try {
      const { data } = await api.post(`/admin/users/${user.id}/reset-password`)
      setCreated({ password: data.temporaryPassword, email: user.email, body: data.message })
      toast.success('Password reset', { description: data.message })
      paged.reload()
    } catch (caught) {
      toast.error('Could not reset the password', { description: errorMessage(caught) })
    }
  }

  async function unlock(user: UserRow) {
    try {
      await api.post(`/admin/users/${user.id}/unlock`)
      toast.success(`${user.fullName} unlocked`)
      paged.reload()
    } catch (caught) {
      toast.error('Could not unlock', { description: errorMessage(caught) })
    }
  }

  const columns: Column<UserRow>[] = [
    {
      key: 'user',
      header: 'User',
      render: (row) => (
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-bold text-primary">
            {row.initials}
          </div>
          <div className="min-w-0">
            <p className="truncate font-medium">{row.fullName}</p>
            <p className="truncate text-[11px] text-muted-foreground">{row.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (row) => (
        <div>
          <Badge variant="secondary">{row.primaryRoleName ?? row.roles[0]}</Badge>
          <p className="mt-0.5 text-[11px] text-muted-foreground">
            level {row.hierarchyLevel} · {row.permissionCount} permissions
          </p>
        </div>
      ),
    },
    {
      key: 'position',
      header: 'Position',
      render: (row) => (
        <div>
          <p>{row.position ?? '—'}</p>
          <p className="text-[11px] text-muted-foreground">{row.department ?? '—'}</p>
        </div>
      ),
    },
    {
      key: 'branch',
      header: 'Branch',
      render: (row) => <span className="text-muted-foreground">{row.branch ?? '—'}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <div className="flex flex-col items-start gap-1">
          <StatusBadge status={row.status} />
          {row.locked && <Badge variant="danger">Locked</Badge>}
          {row.firstLogin && <Badge variant="warning">Must set password</Badge>}
        </div>
      ),
    },
    {
      key: 'lastLogin',
      header: 'Last sign-in',
      render: (row) => (
        <div>
          <p className="text-muted-foreground">{timeAgo(row.lastLoginAt)}</p>
          {row.lastLoginIp && (
            <p className="font-mono text-[11px] text-muted-foreground/70">{row.lastLoginIp}</p>
          )}
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (row) => (
        <div className="flex justify-end gap-1">
          {can('admin.user.reset_password') && (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => resetPassword(row)}
              title="Issue a new temporary password"
            >
              <KeyRound />
            </Button>
          )}
          {can('admin.user.update') && row.locked && (
            <Button variant="ghost" size="sm" onClick={() => unlock(row)} title="Clear lockout">
              <Unlock />
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        title="Users & Roles"
        subtitle="Accounts are created by administrators — there is no public sign-up. Role hierarchy is enforced server-side."
        action={
          can('admin.user.create') && (
            <Button onClick={() => setCreateOpen(true)}>
              <UserPlus /> Add user
            </Button>
          )
        }
      />

      <StatStrip
        stats={[
          { label: 'Users', value: number(paged.totalElements) },
          { label: 'Roles defined', value: number(roles.data?.length) },
          {
            label: 'Pending invitations',
            value: number(invitations.data?.filter((item) => item.status === 'PENDING').length),
            tone: 'warning',
          },
          {
            label: 'You can assign',
            value: `${number(assignable.data?.length)} roles`,
          },
        ]}
      />

      <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="users">Users</TabsTrigger>
          <TabsTrigger value="invitations">Invitations</TabsTrigger>
          <TabsTrigger value="roles">Roles & permissions</TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === 'users' && (
        <ResourceTable<UserRow>
          title="User accounts"
          columns={columns}
          rows={paged.rows}
          loading={paged.loading}
          error={paged.error}
          reload={paged.reload}
          search={paged.search}
          onSearchChange={paged.setSearch}
          searchPlaceholder="Name, email or number"
          page={paged.page}
          totalPages={paged.totalPages}
          totalElements={paged.totalElements}
          onPageChange={paged.setPage}
          emptyTitle="No users"
        />
      )}

      {tab === 'invitations' && (
        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Account invitations</CardTitle>
            <p className="text-xs text-muted-foreground">
              In production these are emailed. With no mail server wired up, the rendered
              message is stored and shown here so the first-login flow stays testable.
            </p>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={invitations.loading}
              error={invitations.error}
              empty={(invitations.data?.length ?? 0) === 0}
              onRetry={invitations.reload}
              emptyTitle="No invitations issued"
            >
              <div className="divide-y">
                {(invitations.data ?? []).map((invitation) => (
                  <div key={invitation.id} className="p-4">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold">{invitation.userName}</p>
                        <p className="truncate text-xs text-muted-foreground">{invitation.email}</p>
                      </div>
                      <div className="flex shrink-0 items-center gap-1.5">
                        <StatusBadge status={invitation.status} />
                        {invitation.expired && invitation.status === 'PENDING' && (
                          <Badge variant="danger">Expired</Badge>
                        )}
                      </div>
                    </div>

                    <p className="mt-1.5 text-[11px] text-muted-foreground">
                      Invited by {invitation.invitedBy ?? 'system'} ·{' '}
                      {invitation.acceptedAt
                        ? `accepted ${timeAgo(invitation.acceptedAt)}`
                        : `expires ${dateShort(invitation.expiresAt)}`}
                    </p>

                    {invitation.temporaryPassword && (
                      <div className="mt-2 flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 p-2.5 dark:border-amber-900 dark:bg-amber-950/40">
                        <Mail className="h-3.5 w-3.5 shrink-0 text-amber-600" />
                        <span className="text-[11px] text-amber-900 dark:text-amber-200">
                          Temporary password:
                        </span>
                        <code className="rounded bg-white px-1.5 py-0.5 font-mono text-xs font-bold dark:bg-black/40">
                          {invitation.temporaryPassword}
                        </code>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            void navigator.clipboard.writeText(invitation.temporaryPassword ?? '')
                            toast.success('Copied')
                          }}
                        >
                          <Copy />
                        </Button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </DataState>
          </CardContent>
        </Card>
      )}

      {tab === 'roles' && (
        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Roles</CardTitle>
            <p className="text-xs text-muted-foreground">
              A lower level number is more privileged. You can only create or assign roles
              below your own level — that rule is enforced on the server, not just hidden in
              the form.
            </p>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={roles.loading}
              error={roles.error}
              empty={(roles.data?.length ?? 0) === 0}
              onRetry={roles.reload}
              emptyTitle="No roles"
            >
              <div className="divide-y">
                {(roles.data ?? [])
                  .slice()
                  .sort((left, right) => left.hierarchyLevel - right.hierarchyLevel)
                  .map((role) => {
                    const canAssign = (assignable.data ?? []).some((item) => item.id === role.id)
                    return (
                      <div key={role.id} className="flex items-start gap-3 p-4">
                        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted text-[11px] font-bold">
                          {role.hierarchyLevel}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="text-sm font-semibold">{role.name}</p>
                            <Badge variant="muted">{role.permissionCount} permissions</Badge>
                            {canAssign ? (
                              <Badge variant="success">
                                <ShieldCheck className="mr-0.5 h-3 w-3" /> You can assign
                              </Badge>
                            ) : (
                              <Badge variant="muted">Above your level</Badge>
                            )}
                          </div>
                          <p className="mt-0.5 text-xs text-muted-foreground">{role.description}</p>
                        </div>
                      </div>
                    )
                  })}
              </div>
            </DataState>
          </CardContent>
        </Card>
      )}

      <CreateUserDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        roles={assignable.data ?? []}
        departments={departments.data ?? []}
        branches={branches.data ?? []}
        onCreated={(result) => {
          setCreated(result)
          setCreateOpen(false)
          paged.reload()
          invitations.reload()
        }}
      />

      {/* The generated password is shown once, exactly as an email would deliver it. */}
      <Dialog open={created !== null} onOpenChange={() => setCreated(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Temporary password issued</DialogTitle>
            <DialogDescription>
              This is shown once. In production it would be emailed to {created?.email} instead.
            </DialogDescription>
          </DialogHeader>
          <div className="rounded-lg border bg-muted/50 p-4 text-center">
            <code className="font-mono text-xl font-bold tracking-wider">{created?.password}</code>
          </div>
          <p className="text-xs text-muted-foreground">
            They will be forced to choose their own password at first sign-in.
          </p>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                void navigator.clipboard.writeText(created?.password ?? '')
                toast.success('Copied')
              }}
            >
              <Copy /> Copy
            </Button>
            <Button onClick={() => setCreated(null)}>Done</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

function CreateUserDialog({
  open,
  onClose,
  roles,
  departments,
  branches,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  roles: RoleOption[]
  departments: Lookup[]
  branches: Lookup[]
  onCreated: (result: { password: string; email: string; body: string }) => void
}) {
  const [form, setForm] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    position: '',
    roleId: '',
    departmentId: '',
    branchId: '',
  })
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const { data } = await api.post('/admin/users', {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        phone: form.phone || null,
        position: form.position || null,
        roleId: Number(form.roleId),
        departmentId: form.departmentId ? Number(form.departmentId) : null,
        branchId: form.branchId ? Number(form.branchId) : null,
      })
      toast.success('User created', { description: data.message })
      onCreated({
        password: data.temporaryPassword,
        email: data.user.email,
        body: data.emailPreview,
      })
      setForm({
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        position: '',
        roleId: '',
        departmentId: '',
        branchId: '',
      })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not create the user'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>Add a user</DialogTitle>
          <DialogDescription>
            The system generates a secure temporary password — you never choose one on
            someone else's behalf.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={submit} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="firstName">First name</Label>
              <Input
                id="firstName"
                value={form.firstName}
                onChange={(event) => setForm({ ...form, firstName: event.target.value })}
                className="mt-1.5"
                required
              />
            </div>
            <div>
              <Label htmlFor="lastName">Last name</Label>
              <Input
                id="lastName"
                value={form.lastName}
                onChange={(event) => setForm({ ...form, lastName: event.target.value })}
                className="mt-1.5"
                required
              />
            </div>
          </div>

          <div>
            <Label htmlFor="newEmail">Email</Label>
            <Input
              id="newEmail"
              type="email"
              value={form.email}
              onChange={(event) => setForm({ ...form, email: event.target.value })}
              className="mt-1.5"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                value={form.phone}
                onChange={(event) => setForm({ ...form, phone: event.target.value })}
                className="mt-1.5"
              />
            </div>
            <div>
              <Label htmlFor="position">Position</Label>
              <Input
                id="position"
                value={form.position}
                onChange={(event) => setForm({ ...form, position: event.target.value })}
                className="mt-1.5"
              />
            </div>
          </div>

          <div>
            <Label>Role</Label>
            <Select value={form.roleId} onValueChange={(value) => setForm({ ...form, roleId: value })}>
              <SelectTrigger className="mt-1.5">
                <SelectValue placeholder="Choose a role" />
              </SelectTrigger>
              <SelectContent>
                {roles.map((role) => (
                  <SelectItem key={role.id} value={String(role.id)}>
                    {role.name} · {role.permissionCount} permissions
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="mt-1 text-[11px] text-muted-foreground">
              Only roles below your own level are listed.
            </p>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>Department</Label>
              <Select
                value={form.departmentId}
                onValueChange={(value) => setForm({ ...form, departmentId: value })}
              >
                <SelectTrigger className="mt-1.5">
                  <SelectValue placeholder="Optional" />
                </SelectTrigger>
                <SelectContent>
                  {departments.map((department) => (
                    <SelectItem key={department.id} value={String(department.id)}>
                      {department.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div>
              <Label>Branch</Label>
              <Select
                value={form.branchId}
                onValueChange={(value) => setForm({ ...form, branchId: value })}
              >
                <SelectTrigger className="mt-1.5">
                  <SelectValue placeholder="Optional" />
                </SelectTrigger>
                <SelectContent>
                  {branches.map((branch) => (
                    <SelectItem key={branch.id} value={String(branch.id)}>
                      {branch.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {error && (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {error}
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || !form.roleId}>
              {submitting ? (
                <>
                  <Loader2 className="animate-spin" /> Creating…
                </>
              ) : (
                <>
                  <UserPlus /> Create user
                </>
              )}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
