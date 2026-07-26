import { NavLink } from 'react-router-dom'
import { Store, X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { visibleNavigation } from '@/lib/navigation'
import { useAuth } from '@/store/auth'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Button } from '@/components/ui/button'

/**
 * The navigation rail.
 *
 * Built from the permissions the signed-in user actually holds, so a cashier sees
 * a three-item sidebar and an owner sees the full platform. Hiding a link is a
 * convenience — every route behind it is enforced server-side as well.
 */
export function AppSidebar({
  mobileOpen,
  onClose,
}: {
  mobileOpen: boolean
  onClose: () => void
}) {
  const user = useAuth((state) => state.user)
  const sections = visibleNavigation(user?.permissions ?? [])

  return (
    <>
      {/* Scrim, mobile only. */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={onClose}
          aria-hidden
        />
      )}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-sidebar text-sidebar-foreground transition-transform lg:translate-x-0',
          mobileOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex h-16 shrink-0 items-center gap-2.5 border-b border-sidebar-border px-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
            <Store className="h-4.5 w-4.5 text-primary-foreground" strokeWidth={2.5} />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-bold leading-tight text-white">BIASHARA</p>
            <p className="truncate text-[11px] leading-tight text-sidebar-foreground/70">
              {user?.tenantName ?? 'Business Platform'}
            </p>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="lg:hidden text-sidebar-foreground hover:bg-white/10 hover:text-white"
            onClick={onClose}
            aria-label="Close navigation"
          >
            <X />
          </Button>
        </div>

        <ScrollArea className="flex-1">
          <nav className="space-y-5 px-3 py-4">
            {sections.map((section) => (
              <div key={section.title}>
                <p className="mb-1.5 px-3 text-[10px] font-semibold uppercase tracking-wider text-sidebar-foreground/50">
                  {section.title}
                </p>
                <div className="space-y-0.5">
                  {section.items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      onClick={onClose}
                      className={({ isActive }) =>
                        cn(
                          'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                          isActive
                            ? 'bg-primary text-primary-foreground'
                            : 'text-sidebar-foreground hover:bg-sidebar-accent hover:text-white',
                        )
                      }
                    >
                      <item.icon className="h-4 w-4 shrink-0" />
                      <span className="truncate">{item.label}</span>
                    </NavLink>
                  ))}
                </div>
              </div>
            ))}
          </nav>
        </ScrollArea>

        {/* Role footer: makes it obvious which account is driving the demo. */}
        <div className="shrink-0 border-t border-sidebar-border p-3">
          <div className="rounded-lg bg-sidebar-accent px-3 py-2.5">
            <p className="text-[10px] uppercase tracking-wider text-sidebar-foreground/50">
              Signed in as
            </p>
            <p className="truncate text-sm font-semibold text-white">{user?.fullName}</p>
            <p className="truncate text-[11px] text-sidebar-foreground/70">
              {user?.primaryRoleName ?? user?.roles?.[0]}
            </p>
          </div>
        </div>
      </aside>
    </>
  )
}
