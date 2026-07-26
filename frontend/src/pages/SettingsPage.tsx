import { Building2, Landmark, Shield, Users } from 'lucide-react'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'

interface Lookup {
  id: number
  name: string
  code?: string
  city?: string
  head?: string
  mainBranch?: boolean
}

export default function SettingsPage() {
  const user = useAuth((state) => state.user)
  const departments = useApi<Lookup[]>('/admin/departments')
  const branches = useApi<Lookup[]>('/admin/branches')

  return (
    <>
      <PageHeader
        title="Settings"
        subtitle="Business profile, structure and security posture"
      />

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="flex items-center gap-2 text-sm">
              <Building2 className="h-4 w-4" /> Business profile
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 pt-4 text-sm">
            <Field label="Business name" value={user?.tenantName ?? '—'} />
            <Separator />
            <Field label="Currency" value={user?.currency ?? 'KES'} />
            <Separator />
            <Field label="Your role" value={user?.primaryRoleName ?? '—'} />
            <Separator />
            <Field label="Your department" value={user?.department ?? 'Not assigned'} />
            <Separator />
            <Field label="Your branch" value={user?.branch ?? 'Not assigned'} />
            <Separator />
            <Field
              label="Permissions held"
              value={`${user?.permissions.length ?? 0} of 63`}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="flex items-center gap-2 text-sm">
              <Shield className="h-4 w-4" /> Security
            </CardTitle>
            <p className="text-xs text-muted-foreground">What is enforced on this deployment</p>
          </CardHeader>
          <CardContent className="space-y-2.5 pt-4">
            {[
              { label: 'Permission-based access control', detail: '63 granular permissions, checked per endpoint', on: true },
              { label: 'JWT authentication', detail: 'Short-lived access tokens carrying the permission set', on: true },
              { label: 'Refresh token rotation', detail: 'Stored server-side and revocable; retired on use', on: true },
              { label: 'BCrypt password hashing', detail: 'Only hashes are stored, never plaintext', on: true },
              { label: 'Password policy', detail: 'Length, case, digit and symbol required', on: true },
              { label: 'Account lockout', detail: 'Locks after 5 failed attempts for 15 minutes', on: true },
              { label: 'Forced first-login change', detail: 'Temporary passwords must be replaced', on: true },
              { label: 'Audit logging', detail: 'Every state change recorded with actor and IP', on: true },
              { label: 'Sign-in history', detail: 'Successes and failures, including unknown emails', on: true },
              { label: 'Multi-tenant isolation', detail: 'Every query scoped by tenant from the token', on: true },
              { label: 'Two-factor authentication', detail: 'Modelled but no OTP channel wired up', on: false },
              { label: 'OAuth2 / social sign-in', detail: 'Not implemented in this build', on: false },
            ].map((item) => (
              <div key={item.label} className="flex items-start gap-2.5">
                <Badge variant={item.on ? 'success' : 'muted'} className="mt-0.5 shrink-0">
                  {item.on ? 'On' : 'Not built'}
                </Badge>
                <div className="min-w-0">
                  <p className="text-xs font-medium">{item.label}</p>
                  <p className="text-[11px] text-muted-foreground">{item.detail}</p>
                </div>
              </div>
            ))}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="flex items-center gap-2 text-sm">
              <Users className="h-4 w-4" /> Departments
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={departments.loading}
              error={departments.error}
              empty={(departments.data?.length ?? 0) === 0}
              onRetry={departments.reload}
              emptyTitle="No departments"
            >
              <div className="divide-y">
                {(departments.data ?? []).map((department) => (
                  <div key={department.id} className="flex items-center justify-between gap-3 px-5 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-xs font-medium">{department.name}</p>
                      <p className="text-[11px] text-muted-foreground">
                        Head: {department.head ?? 'Unassigned'}
                      </p>
                    </div>
                    <Badge variant="muted">{department.code}</Badge>
                  </div>
                ))}
              </div>
            </DataState>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="flex items-center gap-2 text-sm">
              <Landmark className="h-4 w-4" /> Branches
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <DataState
              loading={branches.loading}
              error={branches.error}
              empty={(branches.data?.length ?? 0) === 0}
              onRetry={branches.reload}
              emptyTitle="No branches"
            >
              <div className="divide-y">
                {(branches.data ?? []).map((branch) => (
                  <div key={branch.id} className="flex items-center justify-between gap-3 px-5 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-xs font-medium">{branch.name}</p>
                      <p className="text-[11px] text-muted-foreground">{branch.city}</p>
                    </div>
                    <div className="flex shrink-0 gap-1.5">
                      {branch.mainBranch && <Badge variant="default">Main</Badge>}
                      <Badge variant="muted">{branch.code}</Badge>
                    </div>
                  </div>
                ))}
              </div>
            </DataState>
          </CardContent>
        </Card>
      </div>
    </>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-xs font-semibold">{value}</span>
    </div>
  )
}
