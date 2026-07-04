import React, { useCallback, useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import ReactFlow, {
  Background, Controls, MiniMap, Panel,
  addEdge, useNodesState, useEdgesState,
  type Connection, type NodeTypes, BackgroundVariant
} from 'reactflow'
import 'reactflow/dist/style.css'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Save, Play, ChevronLeft, Loader2, CheckCircle2, Info, Pencil, Check, X } from 'lucide-react'
import { workflowsApi, runsApi } from '../api/workflows'
import { useBuilderStore } from '../store/builderStore'
import { FlowNode } from '../components/nodes/FlowNode'
import NodeLibrarySidebar from '../components/NodeLibrarySidebar'
import NodePropertiesPanel from '../components/NodePropertiesPanel'
import type { NodeType } from '../types'
import { nodeTypeConfig } from '../lib/utils'
import type { FbxNodeData } from '../store/builderStore'

const nodeTypes: NodeTypes = { fbxNode: FlowNode }

export default function Builder() {
  const { id: workflowId } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { selectedNode, setSelectedNode, updateNodeData } = useBuilderStore()
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [isDirty, setIsDirty] = useState(false)
  const [saveOk, setSaveOk] = useState(false)
  const [isRenaming, setIsRenaming] = useState(false)
  const [nameDraft, setNameDraft] = useState('')
  const reactFlowWrapper = useRef<HTMLDivElement>(null)
  const [rfInstance, setRfInstance] = useState<any>(null)

  // Load latest version
  const { data: version, isLoading } = useQuery({
    queryKey: ['version', workflowId],
    queryFn: () => workflowsApi.latestVersion(workflowId!),
    enabled: !!workflowId,
    retry: false,
  })

  const { data: workflow } = useQuery({
    queryKey: ['workflow', workflowId],
    queryFn: () => workflowsApi.get(workflowId!),
    enabled: !!workflowId,
  })

  // Hydrate canvas when version loads
  useEffect(() => {
    if (!version?.nodes) return
    const rfNodes = version.nodes.map(n => ({
      id: n.clientNodeId,
      type: 'fbxNode',
      position: { x: n.positionX, y: n.positionY },
      data: {
        label: n.label,
        nodeType: n.type,
        configJson: n.configJson,
        retryMaxAttempts: n.retryMaxAttempts,
        retryBaseBackoffMs: n.retryBaseBackoffMs,
      } as FbxNodeData,
    }))
    const rfEdges = version.edges.map(e => ({
      id: `${e.sourceClientNodeId}->${e.targetClientNodeId}`,
      source: e.sourceClientNodeId,
      target: e.targetClientNodeId,
      label: e.branchLabel,
      type: 'smoothstep',
      animated: false,
      style: { strokeWidth: 2, stroke: '#94a3b8' },
    }))
    setNodes(rfNodes)
    setEdges(rfEdges)
    setIsDirty(false)
  }, [version])

  const onConnect = useCallback((conn: Connection) => {
    setEdges(eds => addEdge({ ...conn, type: 'smoothstep', style: { strokeWidth: 2, stroke: '#94a3b8' } }, eds))
    setIsDirty(true)
  }, [])

  // Drop node from sidebar
  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    const nodeType = e.dataTransfer.getData('nodeType') as NodeType
    if (!nodeType || !reactFlowWrapper.current || !rfInstance) return
    const bounds = reactFlowWrapper.current.getBoundingClientRect()
    const pos = rfInstance.screenToFlowPosition({ x: e.clientX - bounds.left, y: e.clientY - bounds.top })
    const id = `node_${Date.now()}`
    setNodes(ns => [...ns, {
      id, type: 'fbxNode', position: pos,
      data: { label: nodeTypeConfig[nodeType].label, nodeType } as FbxNodeData,
    }])
    setIsDirty(true)
  }, [rfInstance])

  // Save
  const saveMutation = useMutation({
    mutationFn: () => workflowsApi.saveVersion(workflowId!, {
      nodes: nodes.map(n => ({
        clientNodeId: n.id,
        label: n.data.label,
        type: n.data.nodeType,
        positionX: n.position.x,
        positionY: n.position.y,
        configJson: n.data.configJson,
        retryMaxAttempts: n.data.retryMaxAttempts,
        retryBaseBackoffMs: n.data.retryBaseBackoffMs,
      })),
      edges: edges.map(e => ({
        sourceClientNodeId: e.source,
        targetClientNodeId: e.target,
        branchLabel: e.label as string | undefined,
      })),
      publish: true,
    }),
    onSuccess: () => {
      setIsDirty(false)
      setSaveOk(true)
      setTimeout(() => setSaveOk(false), 2000)
    },
  })

  const triggerMutation = useMutation({
    mutationFn: () => runsApi.trigger(workflowId!),
    onSuccess: run => navigate(`/runs/${run.id}`),
  })

  const renameMutation = useMutation({
    mutationFn: (name: string) => workflowsApi.update(workflowId!, {
      name,
      description: workflow?.description,
      category: workflow?.category,
      cronExpression: workflow?.cronExpression,
      isTemplate: workflow?.isTemplate,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workflow', workflowId] })
      qc.invalidateQueries({ queryKey: ['workflows'] })
      setIsRenaming(false)
    },
  })

  function startRename() {
    setNameDraft(workflow?.name ?? '')
    setIsRenaming(true)
  }

  function submitRename() {
    const trimmed = nameDraft.trim()
    if (!trimmed || trimmed === workflow?.name) { setIsRenaming(false); return }
    renameMutation.mutate(trimmed)
  }

  if (isLoading) return (
    <div className="flex items-center justify-center h-screen">
      <Loader2 size={28} className="animate-spin text-brand-500" />
    </div>
  )

  return (
    <div className="flex h-screen flex-col bg-gray-50">
      {/* Topbar */}
      <header className="h-14 bg-white border-b border-gray-100 flex items-center px-4 gap-3 flex-shrink-0 z-10">
        <button onClick={() => navigate('/workflows')} className="btn-ghost !px-2">
          <ChevronLeft size={16} />
        </button>
        <div className="w-px h-5 bg-gray-200" />
        {isRenaming ? (
          <div className="flex items-center gap-1.5">
            <input
              autoFocus
              className="input !py-1 !text-sm !h-8 w-56"
              value={nameDraft}
              maxLength={150}
              onChange={e => setNameDraft(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') submitRename(); if (e.key === 'Escape') setIsRenaming(false) }}
            />
            <button onClick={submitRename} disabled={renameMutation.isPending} className="btn-ghost !p-1.5 text-emerald-600" title="Save name">
              {renameMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Check size={13} />}
            </button>
            <button onClick={() => setIsRenaming(false)} className="btn-ghost !p-1.5 text-gray-400" title="Cancel">
              <X size={13} />
            </button>
          </div>
        ) : (
          <button onClick={startRename} className="flex items-center gap-1.5 group/name" title="Rename workflow">
            <h1 className="text-sm font-semibold text-gray-900 truncate">{workflow?.name ?? 'Workflow Builder'}</h1>
            <Pencil size={11} className="text-gray-300 group-hover/name:text-gray-500 transition-colors" />
          </button>
        )}
        {isDirty && <span className="badge bg-amber-50 text-amber-600 text-xs">Unsaved</span>}
        
        <div className="ml-auto flex items-center gap-2">
          {saveOk && (
            <span className="flex items-center gap-1.5 text-xs text-emerald-600 font-medium">
              <CheckCircle2 size={13} /> Saved
            </span>
          )}
          <button
            onClick={() => saveMutation.mutate()}
            disabled={saveMutation.isPending}
            className="btn-secondary text-xs"
          >
            {saveMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Save size={13} />}
            Save
          </button>
          <button
            onClick={() => triggerMutation.mutate()}
            disabled={triggerMutation.isPending || isDirty}
            className="btn-primary text-xs"
            title={isDirty ? 'Save first to execute' : 'Execute workflow'}
          >
            {triggerMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : <Play size={13} />}
            Execute
          </button>
        </div>
      </header>

      {isDirty && (
        <div className="bg-amber-50 border-b border-amber-100 px-4 py-1.5 flex items-center gap-2 text-xs text-amber-700">
          <Info size={12} />
          You have unsaved changes. Save before executing to run the latest version.
        </div>
      )}

      <div className="flex flex-1 overflow-hidden">
        {/* Node library */}
        <NodeLibrarySidebar />

        {/* Canvas */}
        <div ref={reactFlowWrapper} className="flex-1" onDrop={onDrop} onDragOver={e => e.preventDefault()}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={changes => { onNodesChange(changes); setIsDirty(true) }}
            onEdgesChange={changes => { onEdgesChange(changes); setIsDirty(true) }}
            onConnect={onConnect}
            onInit={setRfInstance}
            nodeTypes={nodeTypes}
            onNodeClick={(_, node) => setSelectedNode(node as any)}
            onPaneClick={() => setSelectedNode(null)}
            fitView
            fitViewOptions={{ padding: 0.3 }}
            defaultEdgeOptions={{ type: 'smoothstep', style: { strokeWidth: 2, stroke: '#94a3b8' } }}
          >
            <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#e2e8f0" />
            <Controls className="!border-gray-100 !shadow-card" />
            <MiniMap
              nodeColor={node => {
                const cat = nodeTypeConfig[node.data?.nodeType as NodeType]?.category
                return cat === 'trigger' ? '#6366f1' : cat === 'integration' ? '#10b981' : cat === 'control' ? '#8b5cf6' : '#f59e0b'
              }}
              className="!border-gray-100 !shadow-card !rounded-xl"
            />
            {!nodes.length && (
              <Panel position="top-center" className="mt-20 pointer-events-none">
                <div className="text-center">
                  <p className="text-sm font-medium text-gray-500 bg-white px-6 py-3 rounded-xl shadow-card border border-gray-100">
                    Drag nodes from the left panel onto the canvas to build your workflow
                  </p>
                </div>
              </Panel>
            )}
          </ReactFlow>
        </div>

        {/* Properties panel */}
        {selectedNode && (
          <NodePropertiesPanel
            node={selectedNode}
            onChange={(field, val) => {
              updateNodeData(selectedNode.id, { [field]: val })
              setNodes(nds => nds.map(n => n.id === selectedNode.id ? { ...n, data: { ...n.data, [field]: val } } : n))
            }}
            onClose={() => setSelectedNode(null)}
          />
        )}
      </div>
    </div>
  )
}