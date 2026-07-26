import type { DemoAccount } from '@/lib/types'

/**
 * The seeded business owner, mirrored from the backend seed (SeedUsers.all()).
 *
 * The sign-in screen normally lists demo logins from /auth/demo-accounts. This
 * copy keeps the one-click owner entry working even when that call has not
 * finished — or fails because the backend is still waking up — so a reviewer is
 * never left staring at an empty form.
 */
export const DEMO_OWNER: DemoAccount = {
  email: 'owner@biashara.demo',
  password: 'Owner@123',
  roleCode: 'BUSINESS_OWNER',
  roleName: 'Business Owner',
  fullName: 'James Kariuki',
  position: 'Managing Director',
  description: 'Full access: every module, business intelligence, users and settings.',
  permissionCount: 63,
  canAccess: ['Dashboard', 'Business Intelligence', 'POS', 'Sales', 'Inventory'],
}
