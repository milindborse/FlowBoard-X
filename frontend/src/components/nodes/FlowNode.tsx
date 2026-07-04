import React, { memo } from 'react'
import { Handle, Position, type NodeProps } from 'reactflow'
import { cn, nodeTypeConfig, nodeStatusConfig } from '../../lib/utils'
import type { FbxNodeData } from '../../store/builderStore'
import type { NodeStatus } from '../../types'

const categoryHeaderColors: Record<string, string> = {
  trigger:     'bg-brand-600',
  processing:  'bg-amber-500',
  integration: 'bg-emerald-600',
  control:     'bg-violet-600',
}

export const FlowNode = memo(({ data, selected }: NodeProps<FbxNodeData>) => {
  const config = nodeTypeConfig[data.nodeType]
  const statusCfg = data.executionStatus
    ? nodeStatusConfig[data.executionStatus as NodeStatus]
    : null
  const headerBg = categoryHeaderColors[config.category] ?? 'bg-gray-600'

  return (
    <div
      className={cn(
        'min-w-[180px] max-w-[220px] rounded-xl overflow-hidden border-2 bg-white transition-all duration-200',
        selected ? 'border-brand-500 shadow-lg shadow-brand-100' : 'border-transparent shadow-card',
        statusCfg ? `${statusCfg.border} border-2` : '',
      )}
    >
      {/* Category color header */}
      <div className={cn('px-3 py-1.5 flex items-center gap-2', headerBg)}>
        <span className="text-white text-xs font-semibold uppercase tracking-wider">
          {config.category}
        </span>
      </div>

      {/* Node body */}
      <div className={cn('px-3 py-3', statusCfg ? statusCfg.bg : 'bg-white')}>
        <div className="flex items-start gap-2">
          <span className="text-lg leading-none mt-0.5">{config.icon}</span>
          <div className="min-w-0">
            <p className="text-sm font-semibold text-gray-900 truncate">{data.label}</p>
            <p className="text-xs text-gray-400 mt-0.5">{config.label}</p>
          </div>
        </div>

        {data.executionStatus && (
          <div className={cn('mt-2 px-2 py-0.5 rounded-full text-xs font-medium inline-flex items-center gap-1', statusCfg?.bg, statusCfg?.color)}>
            <span className={cn('w-1.5 h-1.5 rounded-full inline-block', statusCfg?.color.replace('text-', 'bg-'))} />
            {data.executionStatus}
          </div>
        )}
      </div>

      {/* Handles */}
      {config.category !== 'trigger' && (
        <Handle type="target" position={Position.Top} className="!w-3 !h-3 !bg-gray-400 !border-2 !border-white" />
      )}
      <Handle type="source" position={Position.Bottom} className="!w-3 !h-3 !bg-brand-500 !border-2 !border-white" />
    </div>
  )
})
FlowNode.displayName = 'FlowNode'
