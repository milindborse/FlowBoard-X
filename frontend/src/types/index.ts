export type NodeType =
  | 'MANUAL_TRIGGER' | 'SCHEDULER_TRIGGER' | 'WEBHOOK_TRIGGER'
  | 'CONDITION' | 'TRANSFORM' | 'DELAY' | 'AGGREGATOR'
  | 'HTTP_REQUEST' | 'POSTGRES_QUERY' | 'REDIS_PUBLISH' | 'EMAIL'
  | 'PARALLEL_SPLIT' | 'MERGE' | 'RETRY' | 'APPROVAL'

export type RunStatus = 'PENDING' | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'AWAITING_APPROVAL'
export type NodeStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | 'RETRYING' | 'AWAITING_APPROVAL'
export type TriggerType = 'MANUAL' | 'SCHEDULED' | 'WEBHOOK' | 'REPLAY'

/** Workflow lifecycle status — independent of run/execution status. */
export type WorkflowStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

// ── API envelope types ────────────────────────────────────────────────────────
// Every backend response is now wrapped. api.ts unwraps `.data` for callers, so
// existing pages/components consuming workflowsApi/runsApi are unaffected — these
// types exist mainly for anyone calling `api` (the raw axios client) directly.

export interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export interface ApiErrorResponse {
  success: false
  message: string
  status: number
  error: string
  path: string
  timestamp: string
  details?: string[]
}

export interface Workflow {
  id: string
  name: string
  description?: string
  category?: string
  status: WorkflowStatus
  ownerId?: string
  active: boolean
  isTemplate: boolean
  currentVersionNumber: number
  cronExpression?: string
  createdAt: string
  updatedAt: string
}

/** Payload for the "Create Workflow" modal — step 1, metadata only. */
export interface CreateWorkflowRequest {
  name: string
  description?: string
  category?: string
}

/** Payload for updating workflow metadata later (name remains editable). */
export interface UpdateWorkflowRequest {
  name: string
  description?: string
  category?: string
  cronExpression?: string
  isTemplate?: boolean
}

export interface NodeDto {
  clientNodeId: string
  label: string
  type: NodeType
  positionX: number
  positionY: number
  configJson?: string
  retryMaxAttempts?: number
  retryBaseBackoffMs?: number
}

export interface EdgeDto {
  sourceClientNodeId: string
  targetClientNodeId: string
  branchLabel?: string
}

export interface WorkflowVersion {
  id: string
  versionNumber: number
  published: boolean
  changeSummary?: string
  nodes: NodeDto[]
  edges: EdgeDto[]
  createdAt: string
}

export interface WorkflowRun {
  id: string
  workflowId: string
  workflowName: string
  versionNumber: number
  status: RunStatus
  triggerType: TriggerType
  replayedFromRunId?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
  createdAt: string
}

export interface NodeExecution {
  id: string
  clientNodeId: string
  nodeLabel: string
  status: NodeStatus
  attemptNumber: number
  logOutput?: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  durationMs?: number
}

export interface DashboardStats {
  totalWorkflows: number
  totalRuns: number
  successfulRuns: number
  failedRuns: number
  runningRuns: number
  successRate: number
  avgDurationMs?: number
  recentRuns: WorkflowRun[]
}

export interface AnalyticsData {
  runsPerDay: Array<{ date: string; runs: number }>
  successRateTrend: Array<{ date: string; successRate: number }>
  avgDurationPerWorkflow: Array<{ workflow: string; avgDurationMs: number }>
  nodeFailureFrequency: Array<{ nodeLabel: string; failures: number }>
}

export interface ExecutionEvent {
  type: 'NODE_STARTED' | 'NODE_SUCCEEDED' | 'NODE_FAILED' | 'NODE_RETRYING'
    | 'NODE_SKIPPED' | 'NODE_AWAITING_APPROVAL' | 'RUN_COMPLETED' | 'RUN_FAILED' | 'LOG_LINE'
  runId: string
  nodeId?: string
  nodeLabel?: string
  nodeStatus?: NodeStatus
  attemptNumber?: number
  durationMs?: number
  nextRetryDelayMs?: number
  message?: string
  errorMessage?: string
  output?: Record<string, unknown>
  timestamp: string
}

export interface AuthUser {
  userId: string
  email: string
  displayName: string
  role: string
  token: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// ── Queue Operations Dashboard types ──────────────────────────────────────────

export interface TrendPoint {
  timestamp: string
  value: number
}

export interface WorkerStatus {
  workerId: number
  status: 'IDLE' | 'ACTIVE'
  currentRunId?: string
  currentWorkflowName?: string
  currentNodeLabel?: string
  completedJobs: number
  failedJobs: number
  lastHeartbeat: string
  currentDurationMs: number
}

export type QueueActivityEventType =
  | 'ENQUEUED' | 'DEQUEUED' | 'NODE_STARTED' | 'RETRY' | 'NODE_FAILED' | 'RUN_COMPLETED' | 'RUN_FAILED'

export interface QueueActivityEvent {
  type: QueueActivityEventType
  timestamp: string
  runId?: string
  workflowName?: string
  nodeLabel?: string
  workerId?: number
  message: string
}

export interface QueueMetrics {
  queueLength: number
  peakQueueLength: number
  queueThroughputPerMinute: number
  avgQueueWaitMs: number | null
  workerUtilizationPercent: number
  activeWorkers: number
  idleWorkers: number
  totalWorkers: number
  retryCount: number
  queueDrainRatePerMinute: number
  totalJobsProcessed: number
  workers: WorkerStatus[]
  queueLengthTrend: TrendPoint[]
  workerUtilizationTrend: TrendPoint[]
  queueThroughputTrend: TrendPoint[]
  executionLatencyTrend: TrendPoint[]
  queueWaitTimeTrend: TrendPoint[]
}