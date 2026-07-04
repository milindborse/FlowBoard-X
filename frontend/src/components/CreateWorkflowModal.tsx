import React, { useState } from 'react'
import { X, Loader2, GitBranch } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { workflowsApi } from '../api/workflows'
import type { Workflow, ApiErrorResponse } from '../types'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: (workflow: Workflow) => void
}

const CATEGORY_OPTIONS = ['Finance', 'Marketing', 'Operations', 'Engineering', 'Sales', 'Other']

const MAX_NAME = 150
const MAX_DESCRIPTION = 1000

export default function CreateWorkflowModal({ open, onClose, onCreated }: Props) {
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('')
  const [nameTouched, setNameTouched] = useState(false)

  const trimmedName = name.trim()
  const nameError =
    trimmedName.length === 0 ? 'Workflow name is required'
      : trimmedName.length > MAX_NAME ? `Name must be ${MAX_NAME} characters or fewer`
      : null

  const createMutation = useMutation({
    mutationFn: () => workflowsApi.create({
      name: trimmedName,
      description: description.trim() || undefined,
      category: category || undefined,
    }),
    onSuccess: (workflow) => {
      reset()
      onCreated(workflow)
    },
  })

  const serverError = createMutation.isError
    ? ((createMutation.error as any)?.response?.data as ApiErrorResponse | undefined)
    : undefined

  function reset() {
    setName('')
    setDescription('')
    setCategory('')
    setNameTouched(false)
    createMutation.reset()
  }

  function handleClose() {
    if (createMutation.isPending) return
    reset()
    onClose()
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setNameTouched(true)
    if (nameError) return
    createMutation.mutate()
  }

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm px-4" onClick={handleClose}>
      <div
        className="w-full max-w-md bg-white rounded-2xl shadow-2xl border border-gray-100 overflow-hidden"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-6 pb-4 border-b border-gray-50">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-brand-50 rounded-lg flex items-center justify-center">
              <GitBranch size={17} className="text-brand-600" />
            </div>
            <div>
              <h2 className="text-sm font-semibold text-gray-900">Create Workflow</h2>
              <p className="text-xs text-gray-500 mt-0.5">Name your workflow to get started</p>
            </div>
          </div>
          <button onClick={handleClose} className="btn-ghost !px-2 !py-1.5" aria-label="Close">
            <X size={16} />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {serverError && (
            <div className="text-xs bg-red-50 text-red-600 rounded-lg px-3 py-2 border border-red-100">
              {serverError.message}
              {serverError.details?.length ? (
                <ul className="mt-1 list-disc list-inside">
                  {serverError.details.map((d, i) => <li key={i}>{d}</li>)}
                </ul>
              ) : null}
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1.5">
              Workflow Name <span className="text-red-500">*</span>
            </label>
            <input
              autoFocus
              className={`input ${nameTouched && nameError ? '!border-red-300 focus:!ring-red-200' : ''}`}
              placeholder="e.g. Customer Onboarding Pipeline"
              value={name}
              maxLength={MAX_NAME}
              onChange={e => setName(e.target.value)}
              onBlur={() => setNameTouched(true)}
            />
            {nameTouched && nameError && (
              <p className="text-xs text-red-500 mt-1">{nameError}</p>
            )}
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1.5">
              Description <span className="text-gray-400 font-normal">(optional)</span>
            </label>
            <textarea
              className="input resize-none"
              rows={3}
              placeholder="What does this workflow do?"
              value={description}
              maxLength={MAX_DESCRIPTION}
              onChange={e => setDescription(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1.5">
              Category <span className="text-gray-400 font-normal">(optional)</span>
            </label>
            <select className="input" value={category} onChange={e => setCategory(e.target.value)}>
              <option value="">No category</option>
              {CATEGORY_OPTIONS.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>

          <div className="flex items-center justify-end gap-2 pt-2">
            <button type="button" onClick={handleClose} className="btn-secondary text-xs" disabled={createMutation.isPending}>
              Cancel
            </button>
            <button type="submit" className="btn-primary text-xs" disabled={createMutation.isPending || !!nameError && nameTouched}>
              {createMutation.isPending ? <Loader2 size={13} className="animate-spin" /> : null}
              Create & Open Builder
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}