import { useState } from 'react'
import { KanbanSquare, ListTree } from 'lucide-react'
import { dateShort, moneyWhole, number, percent } from '@/lib/format'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { ResourceTable, StatStrip, type Column } from '@/components/shared/ResourceTable'
import { StatusBadge } from '@/components/shared/StatusBadge'

interface ProjectRow {
  id: number
  code: string
  name: string
  client?: string
  manager?: string
  startDate: string
  endDate?: string
  budget?: number
  actualCost?: number
  contractValue?: number
  profit?: number
  overBudget: boolean
  status: string
  progress?: number
}

interface TaskCard {
  id: number
  title: string
  project?: string
  assignee?: string
  priority?: string
  dueDate?: string
  overdue: boolean
  estimatedHours?: number
  actualHours?: number
}

interface ProjectSummary {
  totalProjects: number
  inProgress: number
  completed: number
  totalTasks: number
  openTasks: number
}

const COLUMN_LABELS: Record<string, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  IN_REVIEW: 'In review',
  DONE: 'Done',
  BLOCKED: 'Blocked',
}

export default function ProjectsPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const [tab, setTab] = useState<'projects' | 'board'>('projects')

  const summary = useApi<ProjectSummary>('/projects/summary')
  const projects = useApi<ProjectRow[]>('/projects')
  const board = useApi<Record<string, TaskCard[]>>('/projects/board')

  const columns: Column<ProjectRow>[] = [
    {
      key: 'project',
      header: 'Project',
      render: (row) => (
        <div className="min-w-0">
          <p className="truncate font-medium">{row.name}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{row.code}</p>
        </div>
      ),
    },
    {
      key: 'client',
      header: 'Client',
      render: (row) => <span className="text-muted-foreground">{row.client ?? 'Internal'}</span>,
    },
    {
      key: 'manager',
      header: 'Manager',
      render: (row) => <span className="text-muted-foreground">{row.manager ?? '—'}</span>,
    },
    { key: 'status', header: 'Status', render: (row) => <StatusBadge status={row.status} /> },
    {
      key: 'progress',
      header: 'Progress',
      render: (row) => (
        <div className="w-24">
          <div className="flex items-baseline justify-between">
            <span className="numeric text-[11px] font-semibold">{percent(row.progress, 0)}</span>
          </div>
          <Progress value={row.progress ?? 0} className="mt-1 h-1.5" />
        </div>
      ),
    },
    { key: 'budget', header: 'Budget', numeric: true, render: (row) => moneyWhole(row.budget, currency) },
    {
      key: 'spent',
      header: 'Spent',
      numeric: true,
      render: (row) => (
        <span className={row.overBudget ? 'font-bold text-red-600' : ''}>
          {moneyWhole(row.actualCost, currency)}
          {row.overBudget && ' ⚠'}
        </span>
      ),
    },
    {
      key: 'value',
      header: 'Contract',
      numeric: true,
      render: (row) =>
        (row.contractValue ?? 0) > 0 ? moneyWhole(row.contractValue, currency) : '—',
    },
    {
      key: 'profit',
      header: 'Profit',
      numeric: true,
      render: (row) =>
        (row.contractValue ?? 0) > 0 ? (
          <span className={(row.profit ?? 0) >= 0 ? 'text-emerald-600' : 'text-red-600'}>
            {moneyWhole(row.profit, currency)}
          </span>
        ) : (
          '—'
        ),
    },
    { key: 'dates', header: 'Timeline', render: (row) => `${dateShort(row.startDate)} → ${dateShort(row.endDate)}` },
  ]

  return (
    <>
      <PageHeader
        title="Projects"
        subtitle="Client engagements, budgets, profitability and a task board"
      />

      <StatStrip
        stats={[
          { label: 'Projects', value: number(summary.data?.totalProjects) },
          { label: 'In progress', value: number(summary.data?.inProgress), tone: 'warning' },
          { label: 'Completed', value: number(summary.data?.completed), tone: 'success' },
          { label: 'Tasks', value: number(summary.data?.totalTasks) },
          { label: 'Open tasks', value: number(summary.data?.openTasks), tone: 'warning' },
        ]}
      />

      <Tabs value={tab} onValueChange={(value) => setTab(value as typeof tab)} className="mb-4">
        <TabsList>
          <TabsTrigger value="projects">
            <ListTree className="mr-1.5 h-3.5 w-3.5" /> Projects
          </TabsTrigger>
          <TabsTrigger value="board">
            <KanbanSquare className="mr-1.5 h-3.5 w-3.5" /> Task board
          </TabsTrigger>
        </TabsList>
      </Tabs>

      {tab === 'projects' ? (
        <ResourceTable<ProjectRow>
          title="All projects"
          columns={columns}
          rows={projects.data ?? []}
          loading={projects.loading}
          error={projects.error}
          reload={projects.reload}
          totalElements={projects.data?.length}
          emptyTitle="No projects"
        />
      ) : (
        <DataState
          loading={board.loading}
          error={board.error}
          empty={!board.data}
          onRetry={board.reload}
        >
          <div className="grid gap-3 md:grid-cols-3 xl:grid-cols-5">
            {Object.entries(board.data ?? {}).map(([status, tasks]) => (
              <Card key={status} className="flex flex-col">
                <CardHeader className="border-b py-3">
                  <CardTitle className="flex items-center justify-between text-xs">
                    {COLUMN_LABELS[status] ?? status}
                    <Badge variant="muted">{tasks.length}</Badge>
                  </CardTitle>
                </CardHeader>
                <CardContent className="flex-1 space-y-2 p-2.5">
                  {tasks.length === 0 ? (
                    <p className="py-6 text-center text-[11px] text-muted-foreground">Empty</p>
                  ) : (
                    tasks.map((task) => (
                      <div key={task.id} className="rounded-lg border bg-card p-2.5">
                        <p className="text-xs font-medium leading-snug">{task.title}</p>
                        {task.project && (
                          <p className="mt-1 truncate text-[11px] text-muted-foreground">
                            {task.project}
                          </p>
                        )}
                        <div className="mt-1.5 flex flex-wrap items-center gap-1">
                          {task.priority && (
                            <Badge
                              variant={
                                task.priority === 'URGENT'
                                  ? 'danger'
                                  : task.priority === 'HIGH'
                                    ? 'warning'
                                    : 'muted'
                              }
                              className="text-[10px]"
                            >
                              {task.priority}
                            </Badge>
                          )}
                          {task.dueDate && (
                            <Badge
                              variant={task.overdue ? 'danger' : 'secondary'}
                              className="text-[10px]"
                            >
                              {dateShort(task.dueDate)}
                            </Badge>
                          )}
                        </div>
                        {task.assignee && (
                          <p className="mt-1.5 truncate text-[10px] text-muted-foreground">
                            {task.assignee}
                          </p>
                        )}
                      </div>
                    ))
                  )}
                </CardContent>
              </Card>
            ))}
          </div>
        </DataState>
      )}
    </>
  )
}
