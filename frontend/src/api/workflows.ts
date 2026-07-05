import api from './client'
import type {
  Workflow, WorkflowVersion, WorkflowRun, NodeExecution, DashboardStats, AnalyticsData, Page,
  ApiResponse, CreateWorkflowRequest, UpdateWorkflowRequest, WorkflowStatus,
  QueueMetrics, QueueActivityEvent,
} from '../types'

// Every backend response we control is wrapped as { success, message, data, timestamp }.
// BUT: AnalyticsController.java was never shared with/modified by us, so /analytics/* endpoints
// may still return the raw object directly (no wrapper). This unwrap function tolerates both
// shapes so a controller we haven't touched yet doesn't silently break its page.
const unwrap = <T,>(res: { data: any }): T => {
  const body = res.data
  if (body && typeof body === 'object' && 'success' in body && 'data' in body) {
    return body.data as T // new wrapped ApiResponse shape
  }
  return body as T // legacy raw shape (e.g. AnalyticsController, until it's upgraded too)
}

export const workflowsApi = {
  list: (params?: { name?: string; status?: WorkflowStatus; version?: number; page?: number; size?: number }) =>
    api.get<ApiResponse<Page<Workflow>>>('/workflows', {
      params: { name: params?.name, status: params?.status, version: params?.version, page: params?.page ?? 0, size: params?.size ?? 20 },
    }).then(unwrap),

  get: (id: string) => api.get<ApiResponse<Workflow>>(`/workflows/${id}`).then(unwrap),

  /** Step 1 of the create flow: saves metadata only. Open the builder after this resolves. */
  create: (data: CreateWorkflowRequest) =>
    api.post<ApiResponse<Workflow>>('/workflows', data).then(unwrap),

  update: (id: string, data: UpdateWorkflowRequest) =>
    api.put<ApiResponse<Workflow>>(`/workflows/${id}`, data).then(unwrap),

  publish: (id: string) =>
    api.patch<ApiResponse<Workflow>>(`/workflows/${id}/publish`).then(unwrap),

  delete: (id: string) => api.delete(`/workflows/${id}`),

  templates: () => api.get<ApiResponse<Workflow[]>>('/workflows/templates').then(unwrap),

  saveVersion: (id: string, data: object) =>
    api.post<ApiResponse<WorkflowVersion>>(`/workflows/${id}/versions`, data).then(unwrap),

  latestVersion: (id: string) =>
    api.get<ApiResponse<WorkflowVersion>>(`/workflows/${id}/versions/latest`).then(unwrap),

  getVersion: (id: string, version: number) =>
    api.get<ApiResponse<WorkflowVersion>>(`/workflows/${id}/versions/${version}`).then(unwrap),
}

export const runsApi = {
  trigger: (workflowId: string, data?: object) =>
    api.post<ApiResponse<WorkflowRun>>(`/workflows/${workflowId}/execute`, data || { triggerType: 'MANUAL' }).then(unwrap),

  list: (workflowId: string, page = 0) =>
    api.get<ApiResponse<Page<WorkflowRun>>>(`/workflows/${workflowId}/history`, { params: { page, size: 20 } }).then(unwrap),

  get: (runId: string) => api.get<ApiResponse<WorkflowRun>>(`/runs/${runId}`).then(unwrap),

  getNodes: (runId: string) => api.get<ApiResponse<NodeExecution[]>>(`/runs/${runId}/nodes`).then(unwrap),

  cancel: (runId: string) => api.post(`/runs/${runId}/cancel`),

  approve: (runId: string, nodeId: string) => api.post(`/runs/${runId}/nodes/${nodeId}/approve`),

  replay: (workflowId: string, fromRunId: string, fromNodeId: string) =>
    api.post<ApiResponse<WorkflowRun>>(`/workflows/${workflowId}/execute`, {
      triggerType: 'REPLAY',
      replayFromRunId: fromRunId,
      replayFromNodeId: fromNodeId,
    }).then(unwrap),
}

export const analyticsApi = {
  dashboard: () => api.get<ApiResponse<DashboardStats>>('/analytics/dashboard').then(unwrap),
  analytics: () => api.get<ApiResponse<AnalyticsData>>('/analytics').then(unwrap),
}

export const authApi = {
  login: (email: string, password: string) =>
    api.post<ApiResponse<any>>('/auth/login', { email, password }).then(unwrap),
  register: (email: string, password: string, displayName: string) =>
    api.post<ApiResponse<any>>('/auth/register', { email, password, displayName }).then(unwrap),
}

export const opsApi = {
  queueMetrics: () => api.get<ApiResponse<QueueMetrics>>('/ops/queue-metrics').then(unwrap),
  activityFeed: (limit = 50) => api.get<ApiResponse<QueueActivityEvent[]>>('/ops/activity-feed', { params: { limit } }).then(unwrap),
  resetMetrics: () => api.post('/ops/reset-metrics'),
}