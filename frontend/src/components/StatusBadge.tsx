import React from 'react'
import { cn, runStatusConfig, nodeStatusConfig } from '../lib/utils'
import type { RunStatus, NodeStatus } from '../types'

export function RunStatusBadge({ status }: { status: RunStatus }) {
  const cfg = runStatusConfig[status]
  return (
    <span className={cn('badge', cfg.bg, cfg.color)}>
      <span className={cn('w-1.5 h-1.5 rounded-full', cfg.dot)} />
      {cfg.label}
    </span>
  )
}

export function NodeStatusBadge({ status }: { status: NodeStatus }) {
  const cfg = nodeStatusConfig[status]
  return (
    <span className={cn('badge', cfg.bg, cfg.color)}>
      {status}
    </span>
  )
}
