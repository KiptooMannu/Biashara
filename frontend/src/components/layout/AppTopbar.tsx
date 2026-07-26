import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, LogOut, Menu, Search, ShieldCheck, User as UserIcon } from 'lucide-react'
import { api } from '@/lib/api'
import { timeAgo } from '@/lib/format'
import { useAuth } from '@/store/auth'
import type { NotificationItem } from '@/lib/types'
import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'

export function AppTopbar({ onOpenNav }: { onOpenNav: () => void }) {
  const navigate = useNavigate()
  const user = useAuth((state) => state.user)
  const logout = useAuth((state) => state.logout)
  const [notifications, setNotifications] = useState<NotificationItem[]>([])

  useEffect(() => {
    let cancelled = false
    api
      .get<NotificationItem[]>('/notifications', { params: { size: 15 } })
      .then(({ data }) => {
        if (!cancelled) setNotifications(data)
      })
      .catch(() => {
        // The bell is not worth an error banner; it simply stays empty.
      })
    return () => {
      cancelled = true
    }
  }, [])

  const unread = notifications.filter((item) => !item.read).length

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/80 lg:px-6">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={onOpenNav} aria-label="Open navigation">
        <Menu />
      </Button>

      <div className="relative hidden max-w-sm flex-1 md:block">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="search"
          placeholder="Search products, customers, invoices…"
          className="h-9 w-full rounded-lg border bg-muted/50 pl-9 pr-3 text-sm outline-none placeholder:text-muted-foreground focus:border-ring focus:bg-background"
        />
      </div>

      <div className="ml-auto flex items-center gap-1.5">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="relative" aria-label="Notifications">
              <Bell />
              {unread > 0 && (
                <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] font-bold text-destructive-foreground">
                  {unread > 9 ? '9+' : unread}
                </span>
              )}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-80 p-0">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <p className="text-sm font-semibold">Notifications</p>
              {unread > 0 && <Badge variant="danger">{unread} new</Badge>}
            </div>
            <ScrollArea className="max-h-80">
              {notifications.length === 0 ? (
                <p className="px-4 py-8 text-center text-xs text-muted-foreground">
                  Nothing to show
                </p>
              ) : (
                notifications.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => item.actionUrl && navigate(item.actionUrl)}
                    className={cn(
                      'flex w-full flex-col items-start gap-0.5 border-b border-l-2 px-4 py-3 text-left transition-colors last:border-b-0 hover:bg-muted/60',
                      item.severity === 'CRITICAL' && 'border-l-red-500',
                      item.severity === 'WARNING' && 'border-l-amber-500',
                      item.severity === 'SUCCESS' && 'border-l-emerald-500',
                      item.severity === 'INFO' && 'border-l-sky-500',
                      !item.read && 'bg-accent/40',
                    )}
                  >
                    <span className="text-xs font-semibold">{item.title}</span>
                    <span className="text-xs text-muted-foreground">{item.message}</span>
                    <span className="text-[10px] text-muted-foreground/70">
                      {item.module} · {timeAgo(item.createdOn)}
                    </span>
                  </button>
                ))
              )}
            </ScrollArea>
          </DropdownMenuContent>
        </DropdownMenu>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition-colors hover:bg-muted"
            >
              <Avatar className="h-8 w-8">
                <AvatarFallback className="bg-primary text-xs font-bold text-primary-foreground">
                  {user?.initials}
                </AvatarFallback>
              </Avatar>
              <div className="hidden text-left sm:block">
                <p className="text-xs font-semibold leading-tight">{user?.fullName}</p>
                <p className="text-[11px] leading-tight text-muted-foreground">
                  {user?.primaryRoleName}
                </p>
              </div>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-60">
            <DropdownMenuLabel className="font-normal">
              <p className="text-sm font-semibold">{user?.fullName}</p>
              <p className="text-xs text-muted-foreground">{user?.email}</p>
              <div className="mt-2 flex flex-wrap gap-1">
                {user?.roles.map((role) => (
                  <Badge key={role} variant="secondary" className="text-[10px]">
                    {role}
                  </Badge>
                ))}
              </div>
              <p className="mt-2 text-[11px] text-muted-foreground">
                {user?.permissions.length} permissions · {user?.department ?? 'No department'}
              </p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => navigate('/profile')}>
              <UserIcon className="mr-2 h-4 w-4" /> My profile
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => navigate('/change-password')}>
              <ShieldCheck className="mr-2 h-4 w-4" /> Change password
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleLogout} className="text-destructive focus:text-destructive">
              <LogOut className="mr-2 h-4 w-4" /> Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
