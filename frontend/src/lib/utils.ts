import { clsx, type ClassValue } from 'clsx'
import type { RunStatus, NodeStatus } from '../types'

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs)
}

export function formatDuration(ms?: number): string {
  if (!ms) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`
}

export function formatDate(iso?: string): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function formatRelative(iso?: string): string {
  if (!iso) return '—'
  const diff = Date.now() - new Date(iso).getTime()
  if (diff < 60_000) return 'just now'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`
  return `${Math.floor(diff / 86_400_000)}d ago`
}

export const runStatusConfig: Record<RunStatus, { label: string; color: string; bg: string; dot: string }> = {
  PENDING:           { label: 'Pending',           color: 'text-gray-600',   bg: 'bg-gray-100',   dot: 'bg-gray-400' },
  QUEUED:            { label: 'Queued',             color: 'text-blue-600',   bg: 'bg-blue-50',    dot: 'bg-blue-400' },
  RUNNING:           { label: 'Running',            color: 'text-brand-600',  bg: 'bg-brand-50',   dot: 'bg-brand-500 animate-pulse' },
  SUCCEEDED:         { label: 'Succeeded',          color: 'text-emerald-700',bg: 'bg-emerald-50', dot: 'bg-emerald-500' },
  FAILED:            { label: 'Failed',             color: 'text-red-600',    bg: 'bg-red-50',     dot: 'bg-red-500' },
  CANCELLED:         { label: 'Cancelled',          color: 'text-gray-500',   bg: 'bg-gray-100',   dot: 'bg-gray-400' },
  AWAITING_APPROVAL: { label: 'Awaiting Approval',  color: 'text-amber-700',  bg: 'bg-amber-50',   dot: 'bg-amber-500 animate-pulse' },
}

export const nodeStatusConfig: Record<NodeStatus, { color: string; bg: string; border: string }> = {
  PENDING:           { color: 'text-gray-500',   bg: 'bg-gray-50',    border: 'border-gray-200' },
  RUNNING:           { color: 'text-brand-600',  bg: 'bg-brand-50',   border: 'border-brand-300' },
  SUCCEEDED:         { color: 'text-emerald-700',bg: 'bg-emerald-50', border: 'border-emerald-300' },
  FAILED:            { color: 'text-red-600',    bg: 'bg-red-50',     border: 'border-red-300' },
  SKIPPED:           { color: 'text-gray-400',   bg: 'bg-gray-50',    border: 'border-gray-200' },
  RETRYING:          { color: 'text-amber-600',  bg: 'bg-amber-50',   border: 'border-amber-300' },
  AWAITING_APPROVAL: { color: 'text-amber-700',  bg: 'bg-amber-50',   border: 'border-amber-400' },
}

export const nodeTypeConfig = {
  MANUAL_TRIGGER:    { label: 'Manual Trigger',    category: 'trigger',    color: '#6366f1', icon: '▶' },
  SCHEDULER_TRIGGER: { label: 'Scheduler Trigger', category: 'trigger',    color: '#6366f1', icon: '⏰' },
  WEBHOOK_TRIGGER:   { label: 'Webhook Trigger',   category: 'trigger',    color: '#6366f1', icon: '🔗' },
  CONDITION:         { label: 'Condition',         category: 'processing', color: '#f59e0b', icon: '◆' },
  TRANSFORM:         { label: 'Transform',         category: 'processing', color: '#f59e0b', icon: '⇄' },
  DELAY:             { label: 'Delay',             category: 'processing', color: '#f59e0b', icon: '⏱' },
  AGGREGATOR:        { label: 'Aggregator',        category: 'processing', color: '#f59e0b', icon: '∑' },
  HTTP_REQUEST:      { label: 'HTTP Request',      category: 'integration',color: '#10b981', icon: '🌐' },
  POSTGRES_QUERY:    { label: 'PostgreSQL Query',  category: 'integration',color: '#10b981', icon: '🐘' },
  REDIS_PUBLISH:     { label: 'Redis Publish',     category: 'integration',color: '#10b981', icon: '📡' },
  EMAIL:             { label: 'Email',             category: 'integration',color: '#10b981', icon: '✉️' },
  PARALLEL_SPLIT:    { label: 'Parallel Split',    category: 'control',    color: '#8b5cf6', icon: '⑂' },
  MERGE:             { label: 'Merge',             category: 'control',    color: '#8b5cf6', icon: '⑃' },
  RETRY:             { label: 'Retry',             category: 'control',    color: '#8b5cf6', icon: '↻' },
  APPROVAL:          { label: 'Approval',          category: 'control',    color: '#8b5cf6', icon: '✓' },
}
