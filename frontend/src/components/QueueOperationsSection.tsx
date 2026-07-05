import React, { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Layers, TrendingUp, Gauge, Clock3, Users, UserCheck, UserX,
  RotateCw, Droplets, PackageCheck, Pause, Play, RefreshCw, Download,
  Trash2, Wifi, WifiOff, Activity
} from 'lucide-react'
import { opsApi } from '../api/workflows'
import { useOpsSocket } from '../hooks/useOpsSocket'
import { formatDuration, formatDate } from '../lib/utils'
import type { QueueMetrics, QueueActivityEvent, WorkerStatus } from '../types'

// ── Small presentational pieces (match existing StatCard visual language) ──────

function MetricCard({ label, value, icon: Icon, accent }: {
  label: string; value: string | number; icon: React.ElementType; accent: string
}) {
  return (
    <div className="card flex items-start gap-3 !p-4">
      <div className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${accent}`}>
        <Icon size={16} className="text-white" />
      </div>
      <div className="min-w-0">
        <p className="text-lg font-bold text-gray-900 tabular-nums truncate">{value}</p>
        <p className="text-xs text-gray-500 mt-0.5">{label}</p>
      </div>
    </div>
  )
}

function WorkerCard({ worker }: { worker: WorkerStatus }) {
  const isActive = worker.status === 'ACTIVE'
  return (
    <div className={`rounded-xl border p-3 ${isActive ? 'border-brand-200 bg-brand-50/40' : 'border-gray-100 bg-white'}`}>
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-semibold text-gray-900">Worker-{worker.workerId}</span>
        <span className={`badge ${isActive ? 'bg-brand-100 text-brand-700' : 'bg-gray-100 text-gray-500'}`}>
          <span className={`w-1.5 h-1.5 rounded-full ${isActive ? 'bg-brand-500 animate-pulse' : 'bg-gray-400'}`} />
          {worker.status}
        </span>
      </div>
      {isActive ? (
        <div className="space-y-1">
          <p className="text-xs text-gray-700 truncate font-medium">{worker.currentWorkflowName ?? 'Processing…'}</p>
          {worker.currentNodeLabel && (
            <p className="text-xs text-gray-400 truncate">Node: {worker.currentNodeLabel}</p>
          )}
          <p className="text-xs text-gray-400">{formatDuration(worker.currentDurationMs)} elapsed</p>
        </div>
      ) : (
        <p className="text-xs text-gray-400">Waiting for work</p>
      )}
      <div className="flex items-center gap-3 mt-2 pt-2 border-t border-gray-100 text-xs text-gray-400">
        <span>✓ {worker.completedJobs}</span>
        <span>✗ {worker.failedJobs}</span>
        <span className="ml-auto">{formatDate(worker.lastHeartbeat)}</span>
      </div>
    </div>
  )
}

const ACTIVITY_STYLES: Record<string, { color: string; label: string }> = {
  ENQUEUED:      { color: 'text-blue-500',    label: 'Enqueued' },
  DEQUEUED:      { color: 'text-brand-500',   label: 'Dequeued' },
  NODE_STARTED:  { color: 'text-gray-400',    label: 'Node started' },
  RETRY:         { color: 'text-amber-500',   label: 'Retry' },
  NODE_FAILED:   { color: 'text-red-500',     label: 'Node failed' },
  RUN_COMPLETED: { color: 'text-emerald-500', label: 'Completed' },
  RUN_FAILED:    { color: 'text-red-500',     label: 'Failed' },
}

export default function QueueOperationsSection() {
  const [paused, setPaused] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [liveMetrics, setLiveMetrics] = useState<QueueMetrics | null>(null)
  const [activity, setActivity] = useState<QueueActivityEvent[]>([])
  const feedRef = useRef<HTMLDivElement>(null)

  const { data: polledMetrics, refetch } = useQuery({
    queryKey: ['ops-queue-metrics'],
    queryFn: opsApi.queueMetrics,
    refetchInterval: autoRefresh && !paused ? 5000 : false,
    refetchOnMount: 'always',
  })

  const { data: initialFeed } = useQuery({
    queryKey: ['ops-activity-feed'],
    queryFn: () => opsApi.activityFeed(50),
    refetchOnMount: 'always',
  })

  useEffect(() => {
    if (initialFeed?.length) setActivity(initialFeed)
  }, [initialFeed])

  const { connected, lastError } = useOpsSocket({
    enabled: !paused,
    onMetrics: setLiveMetrics,
    onActivity: (event) => {
      setActivity(prev => [event, ...prev].slice(0, 200))
      setTimeout(() => { if (feedRef.current) feedRef.current.scrollTop = 0 }, 30)
    },
  })

  const metrics = liveMetrics ?? polledMetrics

  function handleExport() {
    if (!metrics) return
    const blob = new Blob([JSON.stringify({ metrics, activity }, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `flowboardx-queue-metrics-${new Date().toISOString()}.json`
    a.click()
    URL.revokeObjectURL(url)
  }

  async function handleReset() {
    if (!confirm('Reset accumulated queue metrics (counters, trends, activity feed)? This does not affect the live queue or running jobs.')) return
    await opsApi.resetMetrics()
    refetch()
    setActivity([])
  }

  return (
    <div className="mt-8">
      {/* Section header + ops toolbar */}
      <div className="flex items-center justify-between mb-4 flex-wrap gap-2">
        <div>
          <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
            <Activity size={17} className="text-brand-500" />
            Queue Operations
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">Live distributed execution telemetry</p>
        </div>

        <div className="flex items-center gap-2">
          {connected ? (
            <span className="badge bg-emerald-50 text-emerald-700 text-xs"><Wifi size={11} /> Live</span>
          ) : (
            <span className="badge bg-gray-100 text-gray-500 text-xs" title={lastError ?? undefined}>
              <WifiOff size={11} /> {paused ? 'Paused' : 'Reconnecting…'}
            </span>
          )}
          <button
            onClick={() => setAutoRefresh(v => !v)}
            className={`btn-ghost text-xs !px-2 !py-1.5 ${autoRefresh ? 'text-brand-600' : 'text-gray-400'}`}
            title="Toggle auto-refresh (polling fallback)"
          >
            <RefreshCw size={13} /> Auto Refresh
          </button>
          <button
            onClick={() => setPaused(v => !v)}
            className="btn-ghost text-xs !px-2 !py-1.5"
            title={paused ? 'Resume live updates' : 'Pause live updates'}
          >
            {paused ? <Play size={13} /> : <Pause size={13} />}
            {paused ? 'Resume' : 'Pause'} Live
          </button>
          <button onClick={handleExport} className="btn-ghost text-xs !px-2 !py-1.5" title="Export current metrics as JSON">
            <Download size={13} /> Export
          </button>
          <button onClick={handleReset} className="btn-ghost text-xs !px-2 !py-1.5 text-red-500 hover:bg-red-50" title="Reset accumulated metrics">
            <Trash2 size={13} /> Reset
          </button>
        </div>
      </div>

      {!metrics ? (
        <div className="card text-center py-8 text-sm text-gray-400">Loading queue metrics…</div>
      ) : (
        <>
          {/* Metric cards grid */}
          <div className="grid grid-cols-2 lg:grid-cols-5 gap-3 mb-4">
            <MetricCard label="Queue Length" value={metrics.queueLength} icon={Layers} accent="bg-blue-500" />
            <MetricCard label="Peak Queue Length" value={metrics.peakQueueLength} icon={TrendingUp} accent="bg-indigo-500" />
            <MetricCard label="Throughput /min" value={metrics.queueThroughputPerMinute} icon={Gauge} accent="bg-violet-500" />
            <MetricCard label="Avg Queue Wait" value={formatDuration(metrics.avgQueueWaitMs ?? undefined)} icon={Clock3} accent="bg-amber-500" />
            <MetricCard label="Worker Utilization" value={`${metrics.workerUtilizationPercent.toFixed(0)}%`} icon={Users} accent="bg-brand-600" />
            <MetricCard label="Active Workers" value={metrics.activeWorkers} icon={UserCheck} accent="bg-emerald-500" />
            <MetricCard label="Idle Workers" value={metrics.idleWorkers} icon={UserX} accent="bg-gray-400" />
            <MetricCard label="Retry Count" value={metrics.retryCount} icon={RotateCw} accent="bg-orange-500" />
            <MetricCard label="Queue Drain Rate /min" value={metrics.queueDrainRatePerMinute.toFixed(0)} icon={Droplets} accent="bg-cyan-500" />
            <MetricCard label="Total Jobs Processed" value={metrics.totalJobsProcessed} icon={PackageCheck} accent="bg-teal-600" />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {/* Worker Status panel */}
            <div className="card">
              <h3 className="text-sm font-semibold text-gray-900 mb-3">Worker Status</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {metrics.workers.map(w => <WorkerCard key={w.workerId} worker={w} />)}
              </div>
            </div>

            {/* Live Queue Activity Feed */}
            <div className="card !p-0 overflow-hidden flex flex-col">
              <div className="px-4 py-3 border-b border-gray-100">
                <h3 className="text-sm font-semibold text-gray-900">Live Queue Activity</h3>
              </div>
              <div ref={feedRef} className="flex-1 overflow-y-auto max-h-80 divide-y divide-gray-50">
                {!activity.length ? (
                  <p className="text-xs text-gray-400 text-center py-8">No activity yet — trigger a workflow to see it here.</p>
                ) : activity.map((event, i) => {
                  const style = ACTIVITY_STYLES[event.type] ?? { color: 'text-gray-400', label: event.type }
                  return (
                    <div key={i} className="px-4 py-2 flex items-start gap-2 text-xs">
                      <span className={`font-semibold flex-shrink-0 ${style.color}`}>{style.label}</span>
                      <span className="text-gray-600 truncate flex-1">{event.message}</span>
                      <span className="text-gray-400 flex-shrink-0">{formatDate(event.timestamp)}</span>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}