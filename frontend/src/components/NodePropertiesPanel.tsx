import React from 'react'
import { X, RefreshCw } from 'lucide-react'
import type { Node } from 'reactflow'
import type { FbxNodeData } from '../store/builderStore'
import { nodeTypeConfig } from '../lib/utils'

interface Props {
  node: Node<FbxNodeData>
  onChange: (field: string, value: unknown) => void
  onClose: () => void
}

export default function NodePropertiesPanel({ node, onChange, onClose }: Props) {
  const cfg = nodeTypeConfig[node.data.nodeType]

  return (
    <aside className="w-72 bg-white border-l border-gray-100 flex flex-col overflow-hidden flex-shrink-0">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-lg">{cfg.icon}</span>
          <div>
            <p className="text-xs font-semibold text-gray-900">{cfg.label}</p>
            <p className="text-xs text-gray-400 capitalize">{cfg.category}</p>
          </div>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
          <X size={15} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-5">
        {/* Label */}
        <div>
          <label className="label">Node Label</label>
          <input
            className="input"
            value={node.data.label}
            onChange={e => onChange('label', e.target.value)}
          />
        </div>

        {/* Config JSON */}
        <div>
          <label className="label">Configuration (JSON)</label>
          <textarea
            className="input font-mono text-xs resize-none h-48"
            value={node.data.configJson ?? '{}'}
            onChange={e => onChange('configJson', e.target.value)}
            spellCheck={false}
          />
          <p className="text-xs text-gray-400 mt-1">
            {cfg.category === 'integration'
              ? 'Provide connection details and query/request config.'
              : 'Override default behavior with node-specific settings.'}
          </p>
        </div>

        {/* Retry */}
        <div>
          <div className="flex items-center gap-2 mb-3">
            <RefreshCw size={13} className="text-gray-500" />
            <label className="label !mb-0">Retry Policy</label>
          </div>
          <div className="space-y-3">
            <div>
              <label className="label text-xs">Max Attempts</label>
              <input
                type="number"
                className="input"
                min={1}
                max={10}
                value={node.data.retryMaxAttempts ?? ''}
                placeholder="Inherit global setting"
                onChange={e => onChange('retryMaxAttempts', e.target.value ? Number(e.target.value) : undefined)}
              />
            </div>
            <div>
              <label className="label text-xs">Base Backoff (ms)</label>
              <input
                type="number"
                className="input"
                min={0}
                value={node.data.retryBaseBackoffMs ?? ''}
                placeholder="Inherit global setting"
                onChange={e => onChange('retryBaseBackoffMs', e.target.value ? Number(e.target.value) : undefined)}
              />
            </div>
          </div>
        </div>

        {/* Node ID (read-only) */}
        <div>
          <label className="label">Node ID</label>
          <input className="input bg-gray-50 text-gray-400 font-mono text-xs" value={node.id} readOnly />
        </div>
      </div>
    </aside>
  )
}
