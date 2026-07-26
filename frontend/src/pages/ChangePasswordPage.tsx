import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, Loader2, Lock, ShieldCheck, X } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { useAuth } from '@/store/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/** The password policy, mirrored from the backend so feedback is immediate. */
const RULES = [
  { label: 'At least 8 characters', test: (value: string) => value.length >= 8 },
  { label: 'One upper case letter', test: (value: string) => /[A-Z]/.test(value) },
  { label: 'One lower case letter', test: (value: string) => /[a-z]/.test(value) },
  { label: 'One number', test: (value: string) => /\d/.test(value) },
  { label: 'One special character', test: (value: string) => /[^A-Za-z0-9]/.test(value) },
]

export default function ChangePasswordPage() {
  const navigate = useNavigate()
  const { user, mustChangePassword, logout } = useAuth()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const satisfied = RULES.map((rule) => rule.test(newPassword))
  const allSatisfied = satisfied.every(Boolean)
  const matches = newPassword.length > 0 && newPassword === confirmPassword

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await api.post('/auth/change-password', { currentPassword, newPassword, confirmPassword })
      toast.success('Password changed', {
        description: 'Please sign in again with your new password.',
      })
      // Every session was revoked server-side, so sign the user out cleanly.
      await logout()
      navigate('/login', { replace: true })
    } catch (caught) {
      setError(errorMessage(caught, 'Could not change your password'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/40 px-6 py-10">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-xl bg-primary">
            <Lock className="h-5 w-5 text-primary-foreground" />
          </div>
          <CardTitle>
            {mustChangePassword ? 'Set your password' : 'Change your password'}
          </CardTitle>
          <CardDescription>
            {mustChangePassword
              ? `Welcome ${user?.fullName?.split(' ')[0]}. Your account was created by an administrator with a temporary password, so you need to choose your own before continuing.`
              : 'Choose a new password. All other sessions will be signed out.'}
          </CardDescription>
        </CardHeader>

        <CardContent>
          <form onSubmit={submit} className="space-y-4">
            <div>
              <Label htmlFor="current">
                {mustChangePassword ? 'Temporary password' : 'Current password'}
              </Label>
              <Input
                id="current"
                type="password"
                autoComplete="current-password"
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
                className="mt-1.5"
                required
              />
            </div>

            <div>
              <Label htmlFor="next">New password</Label>
              <Input
                id="next"
                type="password"
                autoComplete="new-password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="mt-1.5"
                required
              />
            </div>

            <div>
              <Label htmlFor="confirm">Confirm new password</Label>
              <Input
                id="confirm"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                className={cn('mt-1.5', confirmPassword && !matches && 'border-destructive')}
                required
              />
              {confirmPassword && !matches && (
                <p className="mt-1.5 text-xs text-destructive">Passwords do not match</p>
              )}
            </div>

            <div className="rounded-lg bg-muted/60 p-3">
              <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Requirements
              </p>
              <ul className="space-y-1">
                {RULES.map((rule, index) => (
                  <li key={rule.label} className="flex items-center gap-2 text-xs">
                    {satisfied[index] ? (
                      <Check className="h-3.5 w-3.5 shrink-0 text-emerald-600" />
                    ) : (
                      <X className="h-3.5 w-3.5 shrink-0 text-muted-foreground/50" />
                    )}
                    <span
                      className={satisfied[index] ? 'text-foreground' : 'text-muted-foreground'}
                    >
                      {rule.label}
                    </span>
                  </li>
                ))}
              </ul>
            </div>

            {error && (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-3.5 py-2.5 text-xs text-destructive">
                {error}
              </div>
            )}

            <Button
              type="submit"
              className="w-full"
              size="lg"
              disabled={submitting || !allSatisfied || !matches}
            >
              {submitting ? (
                <>
                  <Loader2 className="animate-spin" /> Saving…
                </>
              ) : (
                <>
                  <ShieldCheck /> Set password
                </>
              )}
            </Button>

            {!mustChangePassword && (
              <Button
                type="button"
                variant="ghost"
                className="w-full"
                onClick={() => navigate(-1)}
                disabled={submitting}
              >
                Cancel
              </Button>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
