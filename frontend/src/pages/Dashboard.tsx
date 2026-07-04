import React from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  GitBranch, Play, CheckCircle2, XCircle,
  Clock, TrendingUp, ArrowRight, Loader2, Activity
} from 'lucide-react'
import { analyticsApi } from '../api/workflows'
import { RunStatusBadge } from '../components/StatusBadge'
import { formatDuration, formatRelative } from '../lib/utils'

function StatCard({ label, value, sub, icon: Icon, accent }: {
  label: string; value: string | number; sub?: string
  icon: React.ElementType; accent: string
}) {
  return (
    <div className="card flex items-start gap-4">
      <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${accent}`}>
        <Icon size={20} className="text-white" />
      </div>
      <div>
        <p className="text-2xl font-bold text-gray-900 tabular-nums">{value}</p>
        <p className="text-sm text-gray-500 mt-0.5">{label}</p>
        {sub && <p className="text-xs text-gray-400 mt-1">{sub}</p>}
      </div>
    </div>
  )
}

export default function Dashboard() {
  const { data: stats, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: analyticsApi.dashboard,
    refetchInterval: 15_000,
    refetchIntervalInBackground: true,
    refetchOnMount: 'always',
  })

  if (isLoading) return (
    <div className="flex items-center justify-center h-64">
      <Loader2 size={24} className="animate-spin text-brand-500" />
    </div>
  )

  return (
    <div className="p-8 max-w-7xl mx-auto">
      {/* Header */}
      <div className="mb-8 flex items-end justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-sm text-gray-500 mt-1">Real-time view of your workflow operations</p>
        </div>
        <Link to="/workflows/new" className="btn-primary">
          <GitBranch size={15} />
          New Workflow
        </Link>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <StatCard label="Total Workflows"  value={stats?.totalWorkflows ?? 0} icon={GitBranch} accent="bg-brand-600" />
        <StatCard label="Total Runs"       value={stats?.totalRuns ?? 0}      icon={Play}       accent="bg-violet-600" />
        <StatCard label="Success Rate"     value={`${stats?.successRate ?? 0}%`} sub={`${stats?.successfulRuns ?? 0} succeeded`} icon={TrendingUp} accent="bg-emerald-600" />
        <StatCard label="Avg Duration"     value={formatDuration(stats?.avgDurationMs)} icon={Clock} accent="bg-amber-500" />
      </div>

      {/* Health row */}
      <div className="grid grid-cols-3 gap-4 mb-8">
        <div className="card">
          <div className="flex items-center gap-3 mb-4">
            <CheckCircle2 size={18} className="text-emerald-500" />
            <h3 className="text-sm font-semibold text-gray-900">Successful</h3>
            <span className="ml-auto text-xl font-bold text-emerald-600 tabular-nums">{stats?.successfulRuns ?? 0}</span>
          </div>
          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-emerald-500 rounded-full transition-all duration-700"
              style={{ width: `${stats?.successRate ?? 0}%` }}
            />
          </div>
          <p className="text-xs text-gray-400 mt-2">{stats?.successRate ?? 0}% success rate</p>
        </div>

        <div className="card">
          <div className="flex items-center gap-3 mb-4">
            <XCircle size={18} className="text-red-500" />
            <h3 className="text-sm font-semibold text-gray-900">Failed</h3>
            <span className="ml-auto text-xl font-bold text-red-600 tabular-nums">{stats?.failedRuns ?? 0}</span>
          </div>
          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-red-400 rounded-full transition-all duration-700"
              style={{ width: stats?.totalRuns ? `${(stats.failedRuns / stats.totalRuns) * 100}%` : '0%' }}
            />
          </div>
          <p className="text-xs text-gray-400 mt-2">{stats?.totalRuns ? ((stats.failedRuns / stats.totalRuns) * 100).toFixed(1) : 0}% failure rate</p>
        </div>

        <div className="card">
          <div className="flex items-center gap-3 mb-4">
            <Activity size={18} className="text-brand-500 animate-pulse" />
            <h3 className="text-sm font-semibold text-gray-900">Running Now</h3>
            <span className="ml-auto text-xl font-bold text-brand-600 tabular-nums">{stats?.runningRuns ?? 0}</span>
          </div>
          <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
            <div className="h-full bg-brand-500 rounded-full animate-pulse" style={{ width: '100%' }} />
          </div>
          <p className="text-xs text-gray-400 mt-2">Active executions</p>
        </div>
      </div>

      {/* Recent runs */}
      <div className="card">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-base font-semibold text-gray-900">Recent Runs</h2>
          <Link to="/history" className="btn-ghost text-xs">
            View all <ArrowRight size={13} />
          </Link>
        </div>
        {!stats?.recentRuns?.length ? (
          <div className="text-center py-12">
            <Play size={24} className="text-gray-300 mx-auto mb-3" />
            <p className="text-sm text-gray-500">No runs yet. Execute a workflow to see activity here.</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-50">
            {stats.recentRuns.map(run => (
              <Link
                key={run.id}
                to={`/runs/${run.id}`}
                className="flex items-center gap-4 py-3 hover:bg-gray-50 -mx-6 px-6 transition-colors group"
              >
                <RunStatusBadge status={run.status} />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{run.workflowName}</p>
                  <p className="text-xs text-gray-400">{run.triggerType} · v{run.versionNumber}</p>
                </div>
                <div className="text-right flex-shrink-0">
                  <p className="text-xs text-gray-500">{formatDuration(run.durationMs ?? undefined)}</p>
                  <p className="text-xs text-gray-400">{formatRelative(run.createdAt)}</p>
                </div>
                <ArrowRight size={14} className="text-gray-300 group-hover:text-gray-500 transition-colors" />
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}