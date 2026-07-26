import { useState } from 'react'
import { CalendarCheck, Users, Wallet } from 'lucide-react'
import { dateShort, moneyCompact, moneyWhole, number, percent } from '@/lib/format'
import { useApi, usePagedApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'
import { Chart } from '@/components/charts/Chart'

interface EmployeeRow {
  id: number
  employeeNumber: string
  fullName: string
  email?: string
  department?: string
  branch?: string
  position: string
  employmentType?: string
  hireDate: string
  basicSalary?: number
  allowances?: number
  grossSalary?: number
  performanceScore?: number
  leaveBalance?: number
  hasSystemLogin: boolean
  active: boolean
}

interface AttendanceRow {
  id: number
  employeeName: string
  attendanceDate: string
  checkIn?: string
  checkOut?: string
  status: string
  hoursWorked?: number
  minutesLate?: number
}

interface PayrollRow {
  id: number
  employeeName: string
  period: string
  grossPay?: number
  payeTax?: number
  nssfDeduction?: number
  nhifDeduction?: number
  totalDeductions?: number
  netPay: number
  status: string
}

interface HrSummary {
  totalEmployees: number
  monthlyPayroll: number
  presentToday: number
  lateToday: number
  absentToday: number
  pendingLeave: number
  payrollThisPeriod: number
  headcountByDepartment: { label: string; value: number }[]
  attendanceMix: { label: string; value: number }[]
  topPerformers: EmployeeRow[]
}

export default function HrPage() {
  const { user, can } = useAuth()
  const currency = user?.currency ?? 'KES'
  const [tab, setTab] = useState<'employees' | 'attendance' | 'payroll'>('employees')

  const summary = useApi<HrSummary>('/hr/summary')
  const employees = usePagedApi<EmployeeRow>('/hr/employees')
  const attendance = usePagedApi<AttendanceRow>('/hr/attendance', undefined)
  const canSeePayroll = can('hr.payroll.view')
  const payroll = usePagedApi<PayrollRow>('/hr/payroll')

  const employeeColumns: Column<EmployeeRow>[] = [
    {
      key: 'name',
      header: 'Employee',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{row.fullName}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{row.employeeNumber}</p>
        </div>
      ),
    },
    { key: 'position', header: 'Position', render: (row) => row.position },
    {
      key: 'department',
      header: 'Department',
      render: (row) => <span className="text-muted-foreground">{row.department ?? '—'}</span>,
    },
    {
      key: 'branch',
      header: 'Branch',
      render: (row) => <span className="text-muted-foreground">{row.branch ?? '—'}</span>,
    },
    {
      key: 'type',
      header: 'Contract',
      render: (row) => <Badge variant="secondary">{row.employmentType ?? '—'}</Badge>,
    },
    { key: 'hired', header: 'Hired', render: (row) => dateShort(row.hireDate) },
    ...(canSeePayroll
      ? [
          {
            key: 'salary',
            header: 'Gross pay',
            numeric: true,
            render: (row: EmployeeRow) => moneyWhole(row.grossSalary, currency),
          },
        ]
      : []),
    {
      key: 'performance',
      header: 'Performance',
      numeric: true,
      render: (row) =>
        row.performanceScore != null ? (
          <Badge
            variant={
              row.performanceScore >= 85 ? 'success' : row.performanceScore >= 70 ? 'warning' : 'danger'
            }
          >
            {percent(row.performanceScore, 0)}
          </Badge>
        ) : (
          <span className="text-muted-foreground">—</span>
        ),
    },
    {
      key: 'leave',
      header: 'Leave left',
      numeric: true,
      render: (row) => `${row.leaveBalance ?? 0}d`,
    },
    {
      key: 'login',
      header: 'System login',
      render: (row) =>
        row.hasSystemLogin ? <Badge variant="info">Yes</Badge> : <span className="text-muted-foreground">No</span>,
    },
  ]

  const attendanceColumns: Column<AttendanceRow>[] = [
    { key: 'employee', header: 'Employee', render: (row) => row.employeeName },
    { key: 'date', header: 'Date', render: (row) => dateShort(row.attendanceDate) },
    {
      key: 'in',
      header: 'Check in',
      render: (row) => <span className="numeric">{row.checkIn ?? '—'}</span>,
    },
    {
      key: 'out',
      header: 'Check out',
      render: (row) => <span className="numeric">{row.checkOut ?? '—'}</span>,
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    {
      key: 'hours',
      header: 'Hours',
      numeric: true,
      render: (row) => (row.hoursWorked ? `${row.hoursWorked}h` : '—'),
    },
    {
      key: 'late',
      header: 'Late by',
      numeric: true,
      render: (row) =>
        row.minutesLate ? <span className="text-amber-600">{row.minutesLate}m</span> : '—',
    },
  ]

  const payrollColumns: Column<PayrollRow>[] = [
    { key: 'employee', header: 'Employee', render: (row) => row.employeeName },
    { key: 'period', header: 'Period', render: (row) => <span className="numeric">{row.period}</span> },
    { key: 'gross', header: 'Gross', numeric: true, render: (row) => moneyWhole(row.grossPay, currency) },
    { key: 'paye', header: 'PAYE', numeric: true, render: (row) => moneyWhole(row.payeTax, currency) },
    { key: 'nssf', header: 'NSSF', numeric: true, render: (row) => moneyWhole(row.nssfDeduction, currency) },
    { key: 'nhif', header: 'NHIF', numeric: true, render: (row) => moneyWhole(row.nhifDeduction, currency) },
    {
      key: 'deductions',
      header: 'Deductions',
      numeric: true,
      render: (row) => (
        <span className="text-red-600">({moneyWhole(row.totalDeductions, currency)})</span>
      ),
    },
    {
      key: 'net',
      header: 'Net pay',
      numeric: true,
      render: (row) => <span className="font-bold">{moneyWhole(row.netPay, currency)}</span>,
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
  ]

  return (
    <>
      <PageHeader
        title="Human Resources"
        subtitle="Staff, attendance, leave and payroll with Kenyan statutory deductions"
      />

      <StatStrip
        stats={[
          { label: 'Active staff', value: number(summary.data?.totalEmployees) },
          {
            label: 'Present today',
            value: number(summary.data?.presentToday),
            tone: 'success',
          },
          { label: 'Late today', value: number(summary.data?.lateToday), tone: 'warning' },
          {
            label: 'Absent today',
            value: number(summary.data?.absentToday),
            tone: (summary.data?.absentToday ?? 0) > 0 ? 'danger' : 'default',
          },
          {
            label: 'Pending leave',
            value: number(summary.data?.pendingLeave),
            tone: (summary.data?.pendingLeave ?? 0) > 0 ? 'warning' : 'default',
          },
          ...(canSeePayroll
            ? [
                {
                  label: 'Monthly payroll',
                  value: moneyCompact(summary.data?.monthlyPayroll, currency),
                },
              ]
            : []),
        ]}
      />

      <div className="mb-4 grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Headcount by department</CardTitle>
          </CardHeader>
          <CardContent className="pt-4">
            {(summary.data?.headcountByDepartment?.length ?? 0) === 0 ? (
              <p className="py-10 text-center text-xs text-muted-foreground">No data</p>
            ) : (
              <Chart
                type="bar"
                height={240}
                series={[
                  {
                    name: 'Staff',
                    data: (summary.data?.headcountByDepartment ?? []).map((entry) => Number(entry.value)),
                  },
                ]}
                options={{
                  plotOptions: { bar: { horizontal: true, borderRadius: 4, barHeight: '60%' } },
                  xaxis: {
                    categories: (summary.data?.headcountByDepartment ?? []).map((entry) => entry.label),
                  },
                }}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Attendance mix</CardTitle>
            <p className="text-xs text-muted-foreground">Last 30 days</p>
          </CardHeader>
          <CardContent className="pt-4">
            {(summary.data?.attendanceMix?.length ?? 0) === 0 ? (
              <p className="py-10 text-center text-xs text-muted-foreground">No data</p>
            ) : (
              <Chart
                type="donut"
                height={240}
                series={(summary.data?.attendanceMix ?? []).map((entry) => Number(entry.value))}
                options={{
                  labels: (summary.data?.attendanceMix ?? []).map((entry) =>
                    entry.label
                      .toLowerCase()
                      .split('_')
                      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
                      .join(' '),
                  ),
                  legend: { position: 'bottom', fontSize: '11px' },
                }}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b py-4">
            <CardTitle className="text-sm">Top performers</CardTitle>
            <p className="text-xs text-muted-foreground">By most recent review score</p>
          </CardHeader>
          <CardContent className="space-y-3 pt-4">
            {(summary.data?.topPerformers ?? []).map((employee) => (
              <div key={employee.id}>
                <div className="flex items-baseline justify-between gap-2">
                  <p className="truncate text-xs font-medium">{employee.fullName}</p>
                  <span className="numeric shrink-0 text-xs font-semibold">
                    {percent(employee.performanceScore, 0)}
                  </span>
                </div>
                <Progress value={employee.performanceScore ?? 0} className="mt-1 h-1.5" />
                <p className="mt-0.5 truncate text-[11px] text-muted-foreground">{employee.position}</p>
              </div>
            ))}
            {(summary.data?.topPerformers?.length ?? 0) === 0 && (
              <p className="py-8 text-center text-xs text-muted-foreground">No reviews recorded</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="employees">
            <Users className="mr-1.5 h-3.5 w-3.5" /> Employees
          </TabsTrigger>
          <TabsTrigger value="attendance">
            <CalendarCheck className="mr-1.5 h-3.5 w-3.5" /> Attendance
          </TabsTrigger>
          {canSeePayroll && (
            <TabsTrigger value="payroll">
              <Wallet className="mr-1.5 h-3.5 w-3.5" /> Payroll
            </TabsTrigger>
          )}
        </TabsList>
      </Tabs>

      {tab === 'employees' && (
        <ResourceTable<EmployeeRow>
          title="Staff register"
          columns={employeeColumns}
          rows={employees.rows}
          loading={employees.loading}
          error={employees.error}
          reload={employees.reload}
          page={employees.page}
          totalPages={employees.totalPages}
          totalElements={employees.totalElements}
          onPageChange={employees.setPage}
          emptyTitle="No employees"
        />
      )}

      {tab === 'attendance' && (
        <ResourceTable<AttendanceRow>
          title="Attendance records"
          columns={attendanceColumns}
          rows={attendance.rows}
          loading={attendance.loading}
          error={attendance.error}
          reload={attendance.reload}
          page={attendance.page}
          totalPages={attendance.totalPages}
          totalElements={attendance.totalElements}
          onPageChange={attendance.setPage}
          emptyTitle="No attendance recorded"
        />
      )}

      {tab === 'payroll' && canSeePayroll && (
        <ResourceTable<PayrollRow>
          title="Payroll"
          subtitle="PAYE, NSSF and NHIF calculated from gross pay"
          columns={payrollColumns}
          rows={payroll.rows}
          loading={payroll.loading}
          error={payroll.error}
          reload={payroll.reload}
          page={payroll.page}
          totalPages={payroll.totalPages}
          totalElements={payroll.totalElements}
          onPageChange={payroll.setPage}
          emptyTitle="No payroll runs"
        />
      )}
    </>
  )
}
