import React from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  AreaChart, Area, BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts'
import { Loader2, TrendingUp, Clock, AlertTriangle, Activity, Layers, Gauge, Users, Timer } from 'lucide-react'
import { analyticsApi, opsApi } from '../api/workflows'

const COLORS = { brand: '#6366f1', emerald: '#10b981', amber: '#f59e0b', red: '#ef4444' }

function ChartCard({ title, subtitle, icon: Icon, children }: {
  title: string; subtitle: string; icon: React.ElementType; children: React.ReactNode
}) {
  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-1">
        <Icon size={15} className="text-brand-500" />
        <h3 className="text-sm font-semibold text-gray-900">{title}</h3>
      </div>
      <p className="text-xs text-gray-400 mb-4">{subtitle}</p>
      <div className="h-64">{children}</div>
    </div>
  )
}

export default function Analytics() {
  const { data, isLoading } = useQuery({
    queryKey: ['analytics'],
    queryFn: analyticsApi.analytics,
    refetchInterval: 15_000,
    refetchIntervalInBackground: true,
    refetchOnMount: 'always',
  })

  const { data: opsMetrics } = useQuery({
    queryKey: ['ops-queue-metrics-analytics'],
    queryFn: opsApi.queueMetrics,
    refetchInterval: 5_000,
    refetchOnMount: 'always',
  })

  if (isLoading) return (
    <div className="flex items-center justify-center h-64">
      <Loader2 size={24} className="animate-spin text-brand-500" />
    </div>
  )

  const tooltipStyle = {
    backgroundColor: 'white', border: '1px solid #f1f5f9', borderRadius: 8,
    fontSize: 12, boxShadow: '0 8px 30px rgba(0,0,0,0.08)'
  }

  return (
    <div className="p-8 max-w-7xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">Analytics</h1>
        <p className="text-sm text-gray-500 mt-1">Execution performance over the last 30 days</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <ChartCard title="Runs Per Day" subtitle="Total workflow executions" icon={Activity}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data?.runsPerDay ?? []}>
              <defs>
                <linearGradient id="runsGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={COLORS.brand} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={COLORS.brand} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Area type="monotone" dataKey="runs" stroke={COLORS.brand} strokeWidth={2} fill="url(#runsGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Success Rate Trend" subtitle="Percentage of successful runs by day" icon={TrendingUp}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data?.successRateTrend ?? []}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="successRate" stroke={COLORS.emerald} strokeWidth={2.5} dot={{ r: 3, fill: COLORS.emerald }} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Avg Runtime per Workflow" subtitle="Mean execution duration (ms)" icon={Clock}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data?.avgDurationPerWorkflow ?? []} layout="vertical" margin={{ left: 16 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" horizontal={false} />
              <XAxis type="number" tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis type="category" dataKey="workflow" width={120} tick={{ fontSize: 11, fill: '#475569' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Bar dataKey="avgDurationMs" fill={COLORS.amber} radius={[0, 6, 6, 0]} barSize={16} />
            </BarChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Node Failure Frequency" subtitle="Most common failure points across runs" icon={AlertTriangle}>
          {!data?.nodeFailureFrequency?.length ? (
            <div className="flex items-center justify-center h-full text-sm text-gray-400">
              No node failures recorded yet — that's a good sign.
            </div>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.nodeFailureFrequency}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
                <XAxis dataKey="nodeLabel" tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
                <Tooltip contentStyle={tooltipStyle} />
                <Bar dataKey="failures" fill={COLORS.red} radius={[6, 6, 0, 0]} barSize={24} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </ChartCard>
      </div>

      {/* Queue Operations trends - additive section, existing charts above untouched */}
      <div className="mb-4">
        <h2 className="text-base font-semibold text-gray-900">Queue Operations Trends</h2>
        <p className="text-xs text-gray-400 mt-1">Distributed execution telemetry, sampled every 5s</p>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <ChartCard title="Queue Length Trend" subtitle="Number of runs waiting in the Redis queue" icon={Layers}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={(opsMetrics?.queueLengthTrend ?? []).map(p => ({ time: new Date(p.timestamp).toLocaleTimeString(), value: p.value }))}>
              <defs>
                <linearGradient id="queueLenGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={COLORS.brand} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={COLORS.brand} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Area type="monotone" dataKey="value" stroke={COLORS.brand} strokeWidth={2} fill="url(#queueLenGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Worker Utilization Trend" subtitle="Percentage of workers actively processing" icon={Users}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={(opsMetrics?.workerUtilizationTrend ?? []).map(p => ({ time: new Date(p.timestamp).toLocaleTimeString(), value: p.value }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="value" stroke={COLORS.emerald} strokeWidth={2.5} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Queue Throughput Trend" subtitle="Runs dequeued per minute" icon={Gauge}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={(opsMetrics?.queueThroughputTrend ?? []).map(p => ({ time: new Date(p.timestamp).toLocaleTimeString(), value: p.value }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="value" stroke={COLORS.amber} strokeWidth={2.5} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Execution Latency Trend" subtitle="Per-run duration as runs complete (ms)" icon={Timer}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={(opsMetrics?.executionLatencyTrend ?? []).map(p => ({ time: new Date(p.timestamp).toLocaleTimeString(), value: p.value }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Line type="monotone" dataKey="value" stroke={COLORS.red} strokeWidth={2} dot={{ r: 2, fill: COLORS.red }} />
            </LineChart>
          </ResponsiveContainer>
        </ChartCard>

        <ChartCard title="Queue Wait Time Trend" subtitle="Average time a run waits before a worker picks it up (ms)" icon={Clock}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={(opsMetrics?.queueWaitTimeTrend ?? []).map(p => ({ time: new Date(p.timestamp).toLocaleTimeString(), value: p.value }))}>
              <defs>
                <linearGradient id="waitTimeGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={COLORS.amber} stopOpacity={0.25} />
                  <stop offset="100%" stopColor={COLORS.amber} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" vertical={false} />
              <XAxis dataKey="time" tick={{ fontSize: 10, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fontSize: 11, fill: '#94a3b8' }} axisLine={false} tickLine={false} />
              <Tooltip contentStyle={tooltipStyle} />
              <Area type="monotone" dataKey="value" stroke={COLORS.amber} strokeWidth={2} fill="url(#waitTimeGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        </ChartCard>
      </div>
    </div>
  )
}