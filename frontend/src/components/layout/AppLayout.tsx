import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { AppSidebar } from './AppSidebar'
import { AppTopbar } from './AppTopbar'

export function AppLayout() {
  const [mobileNavOpen, setMobileNavOpen] = useState(false)

  return (
    <div className="min-h-screen">
      <AppSidebar mobileOpen={mobileNavOpen} onClose={() => setMobileNavOpen(false)} />
      <div className="lg:pl-64">
        <AppTopbar onOpenNav={() => setMobileNavOpen(true)} />
        <main className="p-4 lg:p-6">
          <div className="mx-auto max-w-[1600px] animate-fade-up">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
