import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, Search, GitBranch, MoreHorizontal, Play,
  Edit3, Trash2, Clock, ChevronRight, Loader2
} from 'lucide-react'
import { workflowsApi, runsApi } from '../api/workflows'
import EmptyState from '../components/EmptyState'
import CreateWorkflowModal from '../components/CreateWorkflowModal'
import { formatRelative, nodeTypeConfig } from '../lib/utils'
import type { Workflow } from '../types'

export default function Workflows() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [modalOpen, setModalOpen] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['workflows', search],
    queryFn: () => workflowsApi.list({ name: search }),
  })

  const deleteMutation = useMutation({
    mutationFn: workflowsApi.delete,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['workflows'] }),
  })

  const triggerMutation = useMutation({
    mutationFn: (id: string) => runsApi.trigger(id),
    onSuccess: run => navigate(`/runs/${run.id}`),
  })

  // Called once the modal has successfully saved the workflow's metadata.
  // The builder only opens now — never before the name/description are persisted.
  function handleWorkflowCreated(workflow: Workflow) {
    setModalOpen(false)
    qc.invalidateQueries({ queryKey: ['workflows'] })
    navigate(`/builder/${workflow.id}`)
  }

  return (
    <div className="p-8 max-w-6xl mx-auto">
      {/* Header */}
      <div className="flex items-end justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Workflows</h1>
          <p className="text-sm text-gray-500 mt-1">Design, version, and execute your business workflows</p>
        </div>
        <button onClick={() => setModalOpen(true)} className="btn-primary">
          <Plus size={15} />
          New Workflow
        </button>
      </div>

      {/* Search */}
      <div className="relative mb-6 max-w-sm">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
        <input
          className="input pl-9"
          placeholder="Search workflows..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex justify-center py-20">
          <Loader2 size={24} className="animate-spin text-brand-500" />
        </div>
      ) : !data?.content?.length ? (
        <EmptyState
          icon={GitBranch}
          title="No workflows yet"
          description="Create your first workflow to start orchestrating your business logic visually."
          action={
            <button onClick={() => setModalOpen(true)} className="btn-primary">
              <Plus size={15} /> Create your first workflow
            </button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {data.content.map(wf => (
            <div key={wf.id} className="card hover:shadow-card-hover transition-shadow duration-200 group">
              {/* Top */}
              <div className="flex items-start justify-between mb-3">
                <div className="w-9 h-9 bg-brand-50 rounded-lg flex items-center justify-center">
                  <GitBranch size={17} className="text-brand-600" />
                </div>
                <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                  <button
                    onClick={() => triggerMutation.mutate(wf.id)}
                    className="btn-ghost !px-2 !py-1.5 text-emerald-600 hover:bg-emerald-50"
                    title="Run now"
                  >
                    <Play size={13} />
                  </button>
                  <Link to={`/builder/${wf.id}`} className="btn-ghost !px-2 !py-1.5" title="Edit">
                    <Edit3 size={13} />
                  </Link>
                  <button
                    onClick={() => { if (confirm('Delete this workflow?')) deleteMutation.mutate(wf.id) }}
                    className="btn-ghost !px-2 !py-1.5 text-red-500 hover:bg-red-50"
                    title="Delete"
                  >
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>

              {/* Name + meta */}
              <h3 className="font-semibold text-gray-900 text-sm mb-1 truncate">{wf.name}</h3>
              {wf.description && (
                <p className="text-xs text-gray-500 mb-3 line-clamp-2">{wf.description}</p>
              )}

              {/* Footer */}
              <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-50">
                <div className="flex items-center gap-2">
                  <span className="badge bg-gray-100 text-gray-600">v{wf.currentVersionNumber}</span>
                  {wf.category && (
                    <span className="badge bg-blue-50 text-blue-700">{wf.category}</span>
                  )}
                  {wf.cronExpression && (
                    <span className="badge bg-amber-50 text-amber-700">
                      <Clock size={10} />
                      Scheduled
                    </span>
                  )}
                  {wf.isTemplate && (
                    <span className="badge bg-violet-50 text-violet-700">Template</span>
                  )}
                </div>
                <Link
                  to={`/builder/${wf.id}`}
                  className="text-xs text-gray-400 hover:text-brand-600 flex items-center gap-1 transition-colors"
                >
                  {formatRelative(wf.updatedAt)}
                  <ChevronRight size={12} />
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}

      <CreateWorkflowModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={handleWorkflowCreated}
      />
    </div>
  )
}