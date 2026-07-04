import React, { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import ReactFlow, {
  Background, Controls, useNodesState, useEdgesState, BackgroundVariant
} from 'reactflow'
import 'reactflow/dist/style.css'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import {
  ChevronLeft, RotateCcw, StopCircle, CheckCircle2,
  XCircle, Clock, Loader2, Terminal, AlertCircle, Home, Play
} from 'lucide-react'
import { runsApi } from '../api/workflows'
import { RunStatusBadge } from '../components/StatusBadge'
import { FlowNode } from '../components/nodes/FlowNode'
import { formatDuration, formatDate, nodeTypeConfig, nodeStatusConfig } from '../lib/utils'
import type { ExecutionEvent, NodeStatus, NodeType } from '../types'
import type { NodeTypes } from 'reactflow'
import type { FbxNodeData } from '../store/builderStore'

const nodeTypes: NodeTypes = { fbxNode: FlowNode }

export default function RunViewer() {
  const { runId } = useParams<{ runId: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [logs, setLogs] = useState<string[]>([])
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const logRef = useRef<HTMLDivElement>(null)
  const stompRef = useRef<Client | null>(null)

  const invalidateExecutionLists = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['dashboard'] }),
      qc.invalidateQueries({ queryKey: ['analytics'] }),
      qc.invalidateQueries({ queryKey: ['runs-history'] }),
      qc.invalidateQueries({ queryKey: ['workflows'] }),
      qc.invalidateQueries({ queryKey: ['workflow'] }),
      qc.invalidateQueries({ queryKey: ['workflows-all'] }),
    ])
  }

  const { data: run, refetch: refetchRun } = useQuery({
    queryKey: ['run', runId],
    queryFn: () => runsApi.get(runId!),
    refetchInterval: d => (d.state.data?.status === 'RUNNING' || d.state.data?.status === 'QUEUED') ? 3000 : false,
  })

  const { data: nodeExecutions } = useQuery({
    queryKey: ['run-nodes', runId],
    queryFn: () => runsApi.getNodes(runId!),
    refetchInterval: run?.status === 'RUNNING' ? 2000 : false,
  })

  // Hydrate canvas from node executions
  useEffect(() => {
    if (!nodeExecutions?.length) return
    const existing = nodes.reduce((acc, n) => ({ ...acc, [n.id]: n }), {} as Record<string, any>)
    setNodes(nodeExecutions.map((ne, idx) => ({
      id: ne.clientNodeId,
      type: 'fbxNode',
      position: existing[ne.clientNodeId]?.position ?? { x: idx * 220, y: Math.floor(idx / 3) * 160 },
      data: {
        label: ne.nodeLabel,
        nodeType: 'MANUAL_TRIGGER' as NodeType, // best-effort - real type would come from version nodes
        executionStatus: ne.status,
      } as FbxNodeData,
    })))
  }, [nodeExecutions])

  // WebSocket for live events
  useEffect(() => {
    if (!runId || run?.status === 'SUCCEEDED' || run?.status === 'FAILED' || run?.status === 'CANCELLED') return

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      onConnect: () => {
        client.subscribe(`/topic/runs/${runId}`, msg => {
          const event: ExecutionEvent = JSON.parse(msg.body)
          handleEvent(event)
        })
      },
      reconnectDelay: 3000,
    })
    client.activate()
    stompRef.current = client

    return () => { client.deactivate() }
  }, [runId, run?.status])

  // Backup path: if the run reaches a terminal state via polling (not just the WS event),
  // still invalidate Dashboard/History/Workflows caches. Placed at top level, per Rules of Hooks.
  useEffect(() => {
    if (!run?.status) return
    if (run.status === 'SUCCEEDED' || run.status === 'FAILED' || run.status === 'CANCELLED') {
      void invalidateExecutionLists()
    }
  }, [run?.status])

  const handleEvent = (event: ExecutionEvent) => {
    const ts = new Date(event.timestamp).toLocaleTimeString()

    if (event.type === 'LOG_LINE' && event.message) {
      setLogs(l => [...l, `[${ts}] ${event.message}`])
    } else if (event.nodeId) {
      const msg = event.type === 'NODE_STARTED'    ? `[${ts}] ▶ ${event.nodeLabel} started (attempt ${event.attemptNumber})`
               : event.type === 'NODE_SUCCEEDED'   ? `[${ts}] ✓ ${event.nodeLabel} succeeded in ${formatDuration(event.durationMs)}`
               : event.type === 'NODE_FAILED'       ? `[${ts}] ✗ ${event.nodeLabel} failed: ${event.errorMessage}`
               : event.type === 'NODE_RETRYING'     ? `[${ts}] ↻ ${event.nodeLabel} retrying in ${event.nextRetryDelayMs}ms (attempt ${event.attemptNumber})`
               : event.type === 'NODE_SKIPPED'      ? `[${ts}] ⊘ ${event.nodeLabel} skipped`
               : event.type === 'NODE_AWAITING_APPROVAL' ? `[${ts}] ⏸ ${event.nodeLabel} awaiting approval`
               : null
      if (msg) setLogs(l => [...l, msg])

      // Update node status on canvas
      if (event.nodeStatus) {
        setNodes(ns => ns.map(n => n.id === event.nodeId
          ? { ...n, data: { ...n.data, executionStatus: event.nodeStatus } }
          : n
        ))
      }
    } else if (event.type === 'RUN_COMPLETED' || event.type === 'RUN_FAILED') {
      setLogs(l => [...l, `[${ts}] ════ Run ${event.type === 'RUN_COMPLETED' ? 'completed successfully' : 'failed'} ════`])
      qc.invalidateQueries({ queryKey: ['run', runId] })
      qc.invalidateQueries({ queryKey: ['run-nodes', runId] })
      void invalidateExecutionLists()
    }

    // Auto-scroll logs
    setTimeout(() => {
      if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight
    }, 50)
  }

  const cancelMutation = useMutation({
    mutationFn: () => runsApi.cancel(runId!),
    onSuccess: () => {
      refetchRun()
      qc.invalidateQueries({ queryKey: ['run-nodes', runId] })
      void invalidateExecutionLists()
    },
  })

  const replayMutation = useMutation({
    mutationFn: (nodeId: string) => runsApi.replay(run!.workflowId, runId!, nodeId),
    onSuccess: newRun => navigate(`/runs/${newRun.id}`),
  })

  const approveMutation = useMutation({
    mutationFn: (nodeId: string) => runsApi.approve(runId!, nodeId),
    onSuccess: () => refetchRun(),
  })

  const isLive = run?.status === 'RUNNING' || run?.status === 'QUEUED'
  const isTerminal = run?.status === 'SUCCEEDED' || run?.status === 'FAILED' || run?.status === 'CANCELLED'

  return (
    <div className="flex flex-col h-screen bg-gray-50">
      {/* Header */}
      <header className="h-14 bg-white border-b border-gray-100 flex items-center px-4 gap-3 flex-shrink-0">
        <button
          onClick={async () => {
            await invalidateExecutionLists()
            navigate('/')
          }}
          className="btn-ghost !px-2"
          title="Back to Dashboard"
        >
          <ChevronLeft size={16} />
        </button>
        <div className="w-px h-5 bg-gray-200" />
        <div className="flex items-center gap-3">
          <h1 className="text-sm font-semibold text-gray-900 truncate">{run?.workflowName}</h1>
          {run && <RunStatusBadge status={run.status} />}
          {isLive && <Loader2 size={14} className="animate-spin text-brand-500" />}
        </div>

        <div className="ml-auto flex items-center gap-2">
          {isTerminal && (
            <>
              <button
                onClick={async () => { await invalidateExecutionLists(); navigate('/') }}
                className="btn-secondary text-xs"
              >
                <Home size={13} />
                Dashboard
              </button>
              <button
                onClick={async () => { await invalidateExecutionLists(); navigate('/workflows') }}
                className="btn-primary text-xs"
              >
                <Play size={13} />
                Run New Workflow
              </button>
            </>
          )}
          {run?.status === 'FAILED' && nodeExecutions?.filter(n => n.status === 'FAILED').map(ne => (
            <button key={ne.clientNodeId} onClick={() => replayMutation.mutate(ne.clientNodeId)} className="btn-secondary text-xs">
              <RotateCcw size={13} />
              Replay from "{ne.nodeLabel}"
            </button>
          ))}
          {run?.status === 'AWAITING_APPROVAL' && nodeExecutions?.filter(n => n.status === 'AWAITING_APPROVAL').map(ne => (
            <button key={ne.clientNodeId} onClick={() => approveMutation.mutate(ne.clientNodeId)} className="btn-primary text-xs bg-amber-500 hover:bg-amber-600">
              <CheckCircle2 size={13} />
              Approve "{ne.nodeLabel}"
            </button>
          ))}
          {isLive && (
            <button onClick={() => cancelMutation.mutate()} className="btn-secondary text-xs text-red-600 border-red-200 hover:bg-red-50">
              <StopCircle size={13} />
              Cancel
            </button>
          )}
        </div>
      </header>

      <div className="flex flex-1 overflow-hidden">
        {/* Canvas - live status */}
        <div className="flex-1">
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            nodeTypes={nodeTypes}
            fitView
            nodesDraggable={false}
            nodesConnectable={false}
            elementsSelectable={false}
          >
            <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#e2e8f0" />
            <Controls />
          </ReactFlow>
        </div>

        {/* Right panel - timeline + logs */}
        <div className="w-96 bg-white border-l border-gray-100 flex flex-col">
          {/* Run metadata */}
          <div className="p-4 border-b border-gray-100">
            <div className="grid grid-cols-2 gap-3 text-xs">
              <div>
                <p className="text-gray-400 mb-0.5">Started</p>
                <p className="font-medium text-gray-900">{formatDate(run?.startedAt)}</p>
              </div>
              <div>
                <p className="text-gray-400 mb-0.5">Duration</p>
                <p className="font-medium text-gray-900">
                  {run?.status === 'RUNNING' ? <span className="text-brand-600 animate-pulse">Running…</span> : formatDuration(run?.durationMs ?? undefined)}
                </p>
              </div>
              <div>
                <p className="text-gray-400 mb-0.5">Trigger</p>
                <p className="font-medium text-gray-900">{run?.triggerType}</p>
              </div>
              <div>
                <p className="text-gray-400 mb-0.5">Version</p>
                <p className="font-medium text-gray-900">v{run?.versionNumber}</p>
              </div>
            </div>
            {run?.errorMessage && (
              <div className="mt-3 px-3 py-2 bg-red-50 rounded-lg flex items-start gap-2">
                <AlertCircle size={13} className="text-red-500 mt-0.5 flex-shrink-0" />
                <p className="text-xs text-red-700">{run.errorMessage}</p>
              </div>
            )}
          </div>

          {/* Node timeline */}
          <div className="flex-1 overflow-y-auto">
            <div className="px-4 py-3 border-b border-gray-100">
              <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Node Timeline</p>
            </div>
            <div className="divide-y divide-gray-50">
              {nodeExecutions?.map(ne => {
                const cfg = nodeStatusConfig[ne.status]
                return (
                  <div key={ne.id} className={`px-4 py-3 ${cfg.bg}`}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-xs font-semibold text-gray-900 truncate">{ne.nodeLabel}</span>
                      <span className={`text-xs font-medium ${cfg.color}`}>{ne.status}</span>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-gray-400">
                      <span>{formatDuration(ne.durationMs ?? undefined)}</span>
                      {ne.attemptNumber > 1 && <span>Attempt #{ne.attemptNumber}</span>}
                    </div>
                    {ne.errorMessage && (
                      <p className="text-xs text-red-600 mt-1 truncate">{ne.errorMessage}</p>
                    )}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Live log console */}
          <div className="h-52 border-t border-gray-100 bg-gray-950 flex flex-col">
            <div className="px-3 py-2 border-b border-gray-800 flex items-center gap-2">
              <Terminal size={12} className="text-gray-500" />
              <span className="text-xs text-gray-500 font-medium">Live Execution Log</span>
              {isLive && <div className="ml-auto w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />}
            </div>
            <div ref={logRef} className="flex-1 overflow-y-auto p-3 font-mono text-xs text-gray-300 space-y-0.5">
              {!logs.length && (
                <p className="text-gray-600">Waiting for execution events…</p>
              )}
              {logs.map((line, i) => (
                <p key={i} className={
                  line.includes('✓') ? 'text-emerald-400'
                  : line.includes('✗') ? 'text-red-400'
                  : line.includes('↻') ? 'text-amber-400'
                  : line.includes('════') ? 'text-brand-400 font-semibold'
                  : 'text-gray-300'
                }>{line}</p>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}