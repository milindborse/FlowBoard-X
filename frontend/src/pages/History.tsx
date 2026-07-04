import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Search, Filter, ArrowRight, Loader2, History as HistoryIcon } from 'lucide-react'
import { runsApi, workflowsApi } from '../api/workflows'
import { RunStatusBadge } from '../components/StatusBadge'
import EmptyState from '../components/EmptyState'
import { formatDuration, formatDate } from '../lib/utils'
import type { RunStatus } from '../types'

const statusOptions: { label: string; value: RunStatus | '' }[] = [
  { label: 'All',       value: '' },
  { label: 'Running',   value: 'RUNNING' },
  { label: 'Succeeded', value: 'SUCCEEDED' },
  { label: 'Failed',    value: 'FAILED' },
  { label: 'Cancelled', value: 'CANCELLED' },
]

export default function History() {
  const [selectedWorkflow, setSelectedWorkflow] = useState('')
  const [status, setStatus] = useState<RunStatus | ''>('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const { data: workflows } = useQuery({
    queryKey: ['workflows-all'],
    queryFn: () => workflowsApi.list(),
    refetchOnMount: 'always',
    refetchInterval: 30_000,
    refetchIntervalInBackground: true,
  })

  const { data: runs, isLoading } = useQuery({
    queryKey: ['runs-history', selectedWorkflow, page],
    queryFn: () => runsApi.list(selectedWorkflow || '00000000-0000-0000-0000-000000000000', page),
    enabled: !!selectedWorkflow,
    refetchOnMount: 'always',
    refetchInterval: selectedWorkflow ? 5_000 : false,
    refetchIntervalInBackground: true,
  })

  const filteredRuns = runs?.content?.filter(r => {
    if (status && r.status !== status) return false
    if (search && !r.workflowName.toLowerCase().includes(search.toLowerCase())) return false
    return true
  }) ?? []

  return (
    <div className="p-8 max-w-6xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Execution History</h1>
        <p className="text-sm text-gray-500 mt-1">Browse and replay past workflow executions</p>
      </div>

      {/* Filters */}
      <div className="card mb-6">
        <div className="flex items-center gap-3 flex-wrap">
          <div className="relative flex-1 min-w-48">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
            <input
              className="input pl-8 text-xs"
              placeholder="Search by workflow name..."
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
          </div>

          <div className="flex items-center gap-2">
            <Filter size={14} className="text-gray-400" />
            <select
              className="input text-xs !w-auto"
              value={selectedWorkflow}
              onChange={e => { setSelectedWorkflow(e.target.value); setPage(0) }}
            >
              <option value="">All workflows</option>
              {workflows?.content?.map(wf => (
                <option key={wf.id} value={wf.id}>{wf.name}</option>
              ))}
            </select>

            <div className="flex items-center gap-1">
              {statusOptions.map(opt => (
                <button
                  key={opt.value}
                  onClick={() => setStatus(opt.value)}
                  className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors ${
                    status === opt.value
                      ? 'bg-brand-100 text-brand-700'
                      : 'text-gray-500 hover:bg-gray-100'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="card !p-0 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-100">
                {['Workflow', 'Status', 'Trigger', 'Version', 'Duration', 'Started', ''].map(h => (
                  <th key={h} className="text-left text-xs font-semibold text-gray-500 uppercase tracking-wider px-4 py-3">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {isLoading ? (
                <tr><td colSpan={7} className="px-4 py-16 text-center">
                  <Loader2 size={20} className="animate-spin text-brand-400 mx-auto" />
                </td></tr>
              ) : !selectedWorkflow ? (
                <tr><td colSpan={7} className="px-4 py-16 text-center text-sm text-gray-400">
                  Select a workflow above to see its run history
                </td></tr>
              ) : !filteredRuns.length ? (
                <tr><td colSpan={7} className="px-4 py-16">
                  <EmptyState icon={HistoryIcon} title="No runs found" description="Try adjusting your filters." />
                </td></tr>
              ) : filteredRuns.map(run => (
                <tr key={run.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3">
                    <p className="text-sm font-medium text-gray-900">{run.workflowName}</p>
                    <p className="text-xs text-gray-400 font-mono">{run.id.slice(0, 8)}…</p>
                  </td>
                  <td className="px-4 py-3"><RunStatusBadge status={run.status} /></td>
                  <td className="px-4 py-3 text-xs text-gray-600">{run.triggerType}</td>
                  <td className="px-4 py-3 text-xs text-gray-600">v{run.versionNumber}</td>
                  <td className="px-4 py-3 text-xs text-gray-600 tabular-nums">{formatDuration(run.durationMs ?? undefined)}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{formatDate(run.startedAt)}</td>
                  <td className="px-4 py-3">
                    <Link to={`/runs/${run.id}`} className="btn-ghost !px-2 !py-1 text-xs">
                      View <ArrowRight size={12} />
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {runs && runs.totalPages > 1 && (
          <div className="px-4 py-3 border-t border-gray-100 flex items-center justify-between">
            <p className="text-xs text-gray-500">{runs.totalElements} total runs</p>
            <div className="flex items-center gap-2">
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0} className="btn-ghost text-xs !px-2 !py-1">Prev</button>
              <span className="text-xs text-gray-500">Page {page + 1} of {runs.totalPages}</span>
              <button onClick={() => setPage(p => p + 1)} disabled={page >= runs.totalPages - 1} className="btn-ghost text-xs !px-2 !py-1">Next</button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}