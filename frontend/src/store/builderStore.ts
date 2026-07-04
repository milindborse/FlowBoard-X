import { create } from 'zustand'
import type { Node, Edge } from 'reactflow'
import type { NodeType } from '../types'

export interface FbxNodeData {
  label: string
  nodeType: NodeType
  configJson?: string
  retryMaxAttempts?: number
  retryBaseBackoffMs?: number
  executionStatus?: string
}

interface BuilderState {
  nodes: Node<FbxNodeData>[]
  edges: Edge[]
  selectedNode: Node<FbxNodeData> | null
  isDirty: boolean

  setNodes: (nodes: Node<FbxNodeData>[]) => void
  setEdges: (edges: Edge[]) => void
  setSelectedNode: (node: Node<FbxNodeData> | null) => void
  updateNodeData: (id: string, data: Partial<FbxNodeData>) => void
  addNode: (node: Node<FbxNodeData>) => void
  markClean: () => void
  markDirty: () => void
  reset: () => void
}

export const useBuilderStore = create<BuilderState>((set, get) => ({
  nodes: [],
  edges: [],
  selectedNode: null,
  isDirty: false,

  setNodes: nodes => set({ nodes, isDirty: true }),
  setEdges: edges => set({ edges, isDirty: true }),
  setSelectedNode: selectedNode => set({ selectedNode }),

  updateNodeData: (id, data) =>
    set(state => ({
      nodes: state.nodes.map(n => n.id === id ? { ...n, data: { ...n.data, ...data } } : n),
      isDirty: true,
      selectedNode: state.selectedNode?.id === id
        ? { ...state.selectedNode, data: { ...state.selectedNode.data, ...data } }
        : state.selectedNode,
    })),

  addNode: node => set(state => ({ nodes: [...state.nodes, node], isDirty: true })),

  markClean: () => set({ isDirty: false }),
  markDirty: () => set({ isDirty: true }),

  reset: () => set({ nodes: [], edges: [], selectedNode: null, isDirty: false }),
}))
