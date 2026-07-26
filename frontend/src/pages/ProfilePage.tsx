import { Link } from 'react-router-dom'
import { KeyRound } from 'lucide-react'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageHeader } from '@/components/shared/PageHeader'

/**
 * The signed-in user's own view.
 *
 * Lists their permissions grouped by module, which doubles as a plain-language
 * explanation of what their role can and cannot reach.
 */
export default function ProfilePage() {
  const user = useAuth((state) => state.user)
  if (!user) return null

  // Group permission codes by their module prefix.
  const grouped = user.permissions.reduce<Record<string, string[]>>((accumulator, code) => {
    const module = code.split('.')[0]
    accumulator[module] = [...(accumulator[module] ?? []), code]
    return accumulator
  }, {})

  return (
    <>
      <PageHeader
        title="My Profile"
        subtitle="Your account, role and exactly what it grants you"
        action={
          <Button variant="outline" asChild>
            <Link to="/change-password">
              <KeyRound /> Change password
            </Link>
          </Button>
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardContent className="pt-6 text-center">
            <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-xl font-bold text-primary-foreground">
              {user.initials}
            </div>
            <h2 className="mt-3 text-base font-bold">{user.fullName}</h2>
            <p className="text-xs text-muted-foreground">{user.email}</p>
            <Badge variant="secondary" className="mt-2">
              {user.primaryRoleName}
            </Badge>

            <div className="mt-5 space-y-2.5 text-left">
              <Row label="Position" value={user.position ?? '—'} />
              <Row label="Department" value={user.department ?? 'Not assigned'} />
              <Row label="Branch" value={user.branch ?? 'Not assigned'} />
              <Row label="Business" value={user.tenantName ?? '—'} />
              <Row label="Hierarchy level" value={String(user.hierarchyLevel)} />
              <Row label="Permissions" value={`${user.permissions.length} of 63`} />
            </div>
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">What you can do</CardTitle>
            <p className="text-xs text-muted-foreground">
              Every one of these is checked again on the server for each request
            </p>
          </CardHeader>
          <CardContent className="pt-4">
            <div className="space-y-4">
              {Object.entries(grouped)
                .sort(([left], [right]) => left.localeCompare(right))
                .map(([module, codes]) => (
                  <div key={module}>
                    <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                      {module} · {codes.length}
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {codes.sort().map((code) => (
                        <Badge key={code} variant="muted" className="font-mono text-[10px]">
                          {code.split('.').slice(1).join('.')}
                        </Badge>
                      ))}
                    </div>
                  </div>
                ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-xs font-semibold">{value}</span>
    </div>
  )
}
