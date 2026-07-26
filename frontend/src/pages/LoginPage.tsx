import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  BarChart3,
  Boxes,
  Check,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Sparkles,
  Store,
  Users,
  Wand2,
} from 'lucide-react'
import { api, errorMessage } from '@/lib/api'
import { DEMO_OWNER } from '@/lib/demo-accounts'
import { landingRouteFor } from '@/lib/navigation'
import { useAuth } from '@/store/auth'
import type { DemoAccount } from '@/lib/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/** Roles shown as one-click cards; the rest sit behind "show all roles". */
const HEADLINE_ROLES = ['BUSINESS_OWNER', 'GENERAL_MANAGER', 'CASHIER']

export default function LoginPage() {
  const navigate = useNavigate()
  const login = useAuth((state) => state.login)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [accounts, setAccounts] = useState<DemoAccount[]>([])
  const [loadingAccounts, setLoadingAccounts] = useState(true)
  const [showAllRoles, setShowAllRoles] = useState(false)
  const [selected, setSelected] = useState<string | null>(null)

  useEffect(() => {
    api
      .get<DemoAccount[]>('/auth/demo-accounts')
      .then(({ data }) => setAccounts(data.length > 0 ? data : [DEMO_OWNER]))
      // The owner login is seeded, so it stays offered even if the list call fails.
      .catch(() => setAccounts([DEMO_OWNER]))
      .finally(() => setLoadingAccounts(false))
  }, [])

  /** The owner card from the server when present, otherwise the seeded copy. */
  const demoOwner =
    accounts.find((account) => account.roleCode === DEMO_OWNER.roleCode) ?? DEMO_OWNER

  function fill(account: DemoAccount) {
    setEmail(account.email)
    setPassword(account.password)
    setSelected(account.email)
    setError(null)
  }

  async function submit(event: React.FormEvent, override?: DemoAccount) {
    event.preventDefault()
    const useEmail = override?.email ?? email
    const usePassword = override?.password ?? password

    setSubmitting(true)
    setError(null)
    try {
      const result = await login(useEmail, usePassword)
      // A freshly invited account must change its password before anything else.
      if (result.mustChangePassword) {
        navigate('/change-password', { replace: true })
        return
      }
      navigate(landingRouteFor(result.user.permissions), { replace: true })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not sign in'))
    } finally {
      setSubmitting(false)
    }
  }

  const headline = accounts.filter((account) => HEADLINE_ROLES.includes(account.roleCode))
  const others = accounts.filter((account) => !HEADLINE_ROLES.includes(account.roleCode))

  return (
    <div className="flex min-h-screen">
      {/* Left: the pitch. Hidden on small screens where the form is what matters. */}
      <div className="relative hidden w-1/2 flex-col justify-between overflow-hidden bg-sidebar p-12 text-white lg:flex">
        <div
          className="pointer-events-none absolute inset-0 opacity-20"
          style={{
            backgroundImage:
              'radial-gradient(circle at 20% 20%, hsl(161 94% 30%) 0%, transparent 45%), radial-gradient(circle at 80% 70%, hsl(199 89% 48%) 0%, transparent 45%)',
          }}
        />

        <div className="relative">
          <div className="mb-14 flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary">
              <Store className="h-5 w-5" strokeWidth={2.5} />
            </div>
            <span className="text-lg font-bold tracking-tight">BIASHARA</span>
          </div>

          <h1 className="max-w-md text-4xl font-bold leading-tight">
            Stop asking what happened.
            <span className="block text-primary">Start knowing what to do next.</span>
          </h1>
          <p className="mt-5 max-w-md text-sm leading-relaxed text-white/70">
            An AI-powered ERP built for micro, small and medium enterprises. Inventory,
            sales, CRM, procurement, finance, HR and business intelligence in one
            platform — with an assistant that explains the numbers instead of just
            reporting them.
          </p>

          <div className="mt-10 grid max-w-md grid-cols-2 gap-4">
            {[
              { icon: BarChart3, label: 'Executive dashboards', detail: '9 role-specific views' },
              { icon: Sparkles, label: 'AI insights', detail: 'Cause and recommendation' },
              { icon: Boxes, label: 'Inventory intelligence', detail: 'Stockout prediction' },
              { icon: Users, label: 'Enterprise RBAC', detail: '63 granular permissions' },
            ].map((feature) => (
              <div key={feature.label} className="rounded-xl border border-white/10 bg-white/5 p-4">
                <feature.icon className="mb-2 h-4 w-4 text-primary" />
                <p className="text-xs font-semibold">{feature.label}</p>
                <p className="mt-0.5 text-[11px] text-white/50">{feature.detail}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="relative space-y-3">
          <div className="max-w-md rounded-lg border border-white/10 bg-white/5 px-4 py-3">
            <p className="text-[11px] font-semibold uppercase tracking-wider text-primary">
              Demo login
            </p>
            <p className="mt-1 font-mono text-xs text-white/80">
              {DEMO_OWNER.email} · {DEMO_OWNER.password}
            </p>
            <p className="mt-1 text-[11px] text-white/45">
              Business Owner — or use the one-click buttons on the right.
            </p>
          </div>
          <p className="text-[11px] text-white/40">
            Spring Boot 3 · PostgreSQL · React · Multi-tenant SaaS architecture
          </p>
        </div>
      </div>

      {/* Right: sign in. */}
      <div className="flex w-full flex-col justify-center px-6 py-10 lg:w-1/2 lg:px-16">
        <div className="mx-auto w-full max-w-md">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary">
              <Store className="h-4.5 w-4.5 text-primary-foreground" strokeWidth={2.5} />
            </div>
            <span className="font-bold tracking-tight">BIASHARA</span>
          </div>

          <h2 className="text-2xl font-bold tracking-tight">Sign in</h2>
          <p className="mt-1.5 text-sm text-muted-foreground">
            Use the demo credentials below, choose another role, or enter credentials
            directly.
          </p>

          {/* Test credentials, spelled out: one click to fill, one click to be inside. */}
          <div className="mt-6 rounded-xl border border-primary/30 bg-primary/5 p-4">
            <div className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 shrink-0 text-primary" />
              <p className="text-sm font-semibold">Demo credentials — Business Owner</p>
            </div>
            <dl className="mt-3 space-y-1 text-xs">
              <div className="flex gap-2">
                <dt className="w-[68px] shrink-0 text-muted-foreground">Email</dt>
                <dd className="truncate font-mono font-medium">{demoOwner.email}</dd>
              </div>
              <div className="flex gap-2">
                <dt className="w-[68px] shrink-0 text-muted-foreground">Password</dt>
                <dd className="truncate font-mono font-medium">{demoOwner.password}</dd>
              </div>
            </dl>
            <div className="mt-3.5 flex flex-wrap gap-2">
              <Button
                type="button"
                size="sm"
                onClick={(event) => submit(event, demoOwner)}
                disabled={submitting}
              >
                {submitting ? <Loader2 className="animate-spin" /> : <KeyRound />}
                Sign in as owner
              </Button>
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => fill(demoOwner)}
                disabled={submitting}
              >
                <Wand2 /> Fill the form
              </Button>
            </div>
            <p className="mt-2.5 text-[11px] text-muted-foreground">
              Full access to every module — inventory, sales, finance, HR, users and
              settings.
            </p>
          </div>

          {/* One-click role entry: the reviewer never has to be handed a password. */}
          <p className="mt-7 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
            Or enter as another role
          </p>
          <div className="mt-2.5 space-y-2">
            {loadingAccounts ? (
              <>
                <Skeleton className="h-[74px] w-full rounded-xl" />
                <Skeleton className="h-[74px] w-full rounded-xl" />
                <Skeleton className="h-[74px] w-full rounded-xl" />
              </>
            ) : accounts.length === 0 ? (
              <div className="rounded-xl border border-dashed p-4 text-xs text-muted-foreground">
                Demo accounts are unavailable — the backend may still be starting. You can
                still sign in manually below.
              </div>
            ) : (
              <>
                {headline.map((account) => (
                  <DemoCard
                    key={account.email}
                    account={account}
                    selected={selected === account.email}
                    disabled={submitting}
                    onPick={() => fill(account)}
                    onGo={(event) => submit(event, account)}
                  />
                ))}

                {others.length > 0 && (
                  <>
                    <button
                      type="button"
                      onClick={() => setShowAllRoles((open) => !open)}
                      className="w-full pt-1 text-xs font-medium text-primary hover:underline"
                    >
                      {showAllRoles
                        ? 'Hide the other roles'
                        : `Show ${others.length} more roles (admin, finance, HR, procurement…)`}
                    </button>
                    {showAllRoles && (
                      <div className="grid gap-2 pt-1 sm:grid-cols-2">
                        {others.map((account) => (
                          <button
                            key={account.email}
                            type="button"
                            disabled={submitting}
                            onClick={() => fill(account)}
                            className={cn(
                              'rounded-lg border p-2.5 text-left transition-colors hover:border-primary hover:bg-accent',
                              selected === account.email && 'border-primary bg-accent',
                            )}
                          >
                            <p className="truncate text-xs font-semibold">{account.roleName}</p>
                            <p className="truncate text-[11px] text-muted-foreground">
                              {account.permissionCount} permissions
                            </p>
                          </button>
                        ))}
                      </div>
                    )}
                  </>
                )}
              </>
            )}
          </div>

          <div className="my-6 flex items-center gap-3">
            <div className="h-px flex-1 bg-border" />
            <span className="text-[11px] uppercase tracking-wider text-muted-foreground">or</span>
            <div className="h-px flex-1 bg-border" />
          </div>

          <form onSubmit={submit} className="space-y-4">
            <div>
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                autoComplete="username"
                placeholder="you@business.co.ke"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="mt-1.5"
                required
              />
            </div>

            <div>
              <Label htmlFor="password">Password</Label>
              <div className="relative mt-1.5">
                <Input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  className="pr-10"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3.5 py-2.5 text-xs text-destructive">
                {error}
              </div>
            )}

            <Button type="submit" className="w-full" size="lg" disabled={submitting}>
              {submitting ? (
                <>
                  <Loader2 className="animate-spin" /> Signing in…
                </>
              ) : (
                <>
                  <KeyRound /> Sign in
                </>
              )}
            </Button>
          </form>

          <p className="mt-6 text-center text-[11px] leading-relaxed text-muted-foreground">
            Accounts are created by administrators, not by self-registration. There is no
            public sign-up by design — see Users &amp; Roles once signed in as an owner.
          </p>
        </div>
      </div>
    </div>
  )
}

function DemoCard({
  account,
  selected,
  disabled,
  onPick,
  onGo,
}: {
  account: DemoAccount
  selected: boolean
  disabled: boolean
  onPick: () => void
  onGo: (event: React.MouseEvent) => void
}) {
  return (
    <div
      className={cn(
        'group flex items-center gap-3 rounded-xl border p-3 transition-colors',
        selected ? 'border-primary bg-accent' : 'hover:border-primary/50 hover:bg-muted/50',
      )}
    >
      <button type="button" onClick={onPick} disabled={disabled} className="min-w-0 flex-1 text-left">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-semibold">{account.roleName}</p>
          {selected && <Check className="h-3.5 w-3.5 shrink-0 text-primary" />}
        </div>
        <p className="mt-0.5 line-clamp-2 text-[11px] leading-snug text-muted-foreground">
          {account.description}
        </p>
        <div className="mt-1.5 flex flex-wrap gap-1">
          <Badge variant="muted" className="text-[10px]">
            {account.permissionCount} permissions
          </Badge>
          {account.canAccess.slice(0, 2).map((module) => (
            <Badge key={module} variant="secondary" className="text-[10px]">
              {module}
            </Badge>
          ))}
          {account.canAccess.length > 2 && (
            <Badge variant="secondary" className="text-[10px]">
              +{account.canAccess.length - 2}
            </Badge>
          )}
        </div>
      </button>

      <Button
        size="sm"
        variant={selected ? 'default' : 'outline'}
        onClick={onGo}
        disabled={disabled}
        className="shrink-0"
      >
        Enter <ArrowRight />
      </Button>
    </div>
  )
}
