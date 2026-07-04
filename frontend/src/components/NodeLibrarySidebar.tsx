import React, { useState } from 'react'
import { nodeTypeConfig } from '../lib/utils'
import type { NodeType } from '../types'

const categories = [
  { key: 'trigger',     label: 'Triggers',     color: 'text-brand-600 bg-brand-50' },
  { key: 'processing',  label: 'Processing',   color: 'text-amber-600 bg-amber-50' },
  { key: 'integration', label: 'Integrations', color: 'text-emerald-700 bg-emerald-50' },
  { key: 'control',     label: 'Control Flow', color: 'text-violet-700 bg-violet-50' },
]

const grouped = categories.map(cat => ({
  ...cat,
  nodes: (Object.entries(nodeTypeConfig) as [NodeType, typeof nodeTypeConfig[NodeType]][])
    .filter(([, cfg]) => cfg.category === cat.key),
}))

export default function NodeLibrarySidebar() {
  const [expanded, setExpanded] = useState<string[]>(['trigger', 'processing'])

  const toggle = (key: string) =>
    setExpanded(ex => ex.includes(key) ? ex.filter(k => k !== key) : [...ex, key])

  const onDragStart = (e: React.DragEvent, nodeType: NodeType) => {
    e.dataTransfer.setData('nodeType', nodeType)
    e.dataTransfer.effectAllowed = 'move'
  }

  return (
    <aside className="w-56 bg-white border-r border-gray-100 flex flex-col overflow-y-auto flex-shrink-0">
      <div className="px-3 py-3 border-b border-gray-100">
        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Node Library</p>
        <p className="text-xs text-gray-400 mt-0.5">Drag onto canvas</p>
      </div>

      <div className="flex-1 py-2">
        {grouped.map(cat => (
          <div key={cat.key}>
            {/* Category header */}
            <button
              onClick={() => toggle(cat.key)}
              className="w-full flex items-center justify-between px-3 py-2 hover:bg-gray-50 transition-colors"
            >
              <span className={`text-xs font-semibold px-2 py-0.5 rounded-md ${cat.color}`}>
                {cat.label}
              </span>
              <span className="text-gray-400 text-xs">{expanded.includes(cat.key) ? '▲' : '▼'}</span>
            </button>

            {/* Nodes in category */}
            {expanded.includes(cat.key) && (
              <div className="pb-2">
                {cat.nodes.map(([type, cfg]) => (
                  <div
                    key={type}
                    draggable
                    onDragStart={e => onDragStart(e, type)}
                    className="mx-2 my-0.5 flex items-center gap-2.5 px-2.5 py-2 rounded-lg border border-gray-100
                               bg-white hover:border-brand-200 hover:bg-brand-50 cursor-grab active:cursor-grabbing
                               transition-all duration-150 select-none group"
                  >
                    <span className="text-base leading-none">{cfg.icon}</span>
                    <span className="text-xs font-medium text-gray-700 group-hover:text-brand-700 truncate">
                      {cfg.label}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </aside>
  )
}
