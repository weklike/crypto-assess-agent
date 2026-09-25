import { ApiError } from './client'
import type { ProblemDetail } from './types'

export type AssessmentStatus = 'DRAFT' | 'READY' | 'ANALYZING' | 'REVIEW' | 'FAILED' | 'CONFIRMED' | 'SCORED' | 'REPORTED'

export interface AssessProject {
  id: number
  name: string
  systemName: string
  level: number
  status: AssessmentStatus
  lastError: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface Measure {
  algorithm?: string | null
  protocol?: string | null
  product?: string | null
  evidence?: string | null
}

export interface CryptoMeasures {
  transport: Measure[]
  storage: Measure[]
  auth: Measure[]
  key_mgmt: Measure[]
  other: Measure[]
}

export interface ObjectView {
  id: number
  layer: string
  name: string
  description: string | null
  measures: CryptoMeasures
}

export interface AssessFinding {
  id: number
  projectId: number
  objectId: number
  clauseRef: string
  judgment: string
  evidence: string | null
  ruleHitsJson: unknown
  rationale: string | null
  remediation: string | null
  missingInfoJson: unknown
  source: string
  llmCallId: number | null
  reviewed: boolean
  reviewerNote: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
  /** 技术层面的 D（密码使用有效性）/ A（算法与技术合规性）/ K（密钥管理安全）；管理层面为 null */
  dimD: boolean | null
  dimA: boolean | null
  dimK: boolean | null
  ra: number | null
  rk: number | null
  moduleLevel: number | null
}

export interface ReviewBody {
  judgment?: string
  note?: string
  reviewed?: boolean
  d?: boolean | null
  a?: boolean | null
  k?: boolean | null
  ra?: number | null
  rk?: number | null
}

export interface AssessStep {
  id: number
  objectId: number
  step: string
  stepKey: string
  status: string
  attempts: number
  error: string | null
}

export interface AssessScore {
  scope: 'total' | 'group' | 'layer' | 'unit' | 'object'
  scopeKey: string
  /** 不出总分或层面全部不适用时为 null */
  score: number | null
  ruleVersion: string
  /** total 行的 note 写明不出总分的原因 */
  detailJson?: { note?: string | null; [key: string]: unknown } | null
}

export interface AssessmentDetail {
  project: AssessProject
  objects: ObjectView[]
  steps: AssessStep[]
  findings: AssessFinding[]
  scores: AssessScore[]
}

export const JUDGMENTS = ['符合', '部分符合', '不符合', '不适用']

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })
  if (!response.ok) {
    let problem: ProblemDetail = { status: response.status, title: response.statusText }
    try {
      problem = await response.json()
    } catch {
      // 保留状态码
    }
    throw new ApiError(response.status, problem)
  }
  return response.json()
}

export const assessmentsApi = {
  list: () => request<AssessProject[]>('/api/assessments'),
  create: (body: { name: string; systemName: string; level: number }) =>
    request<AssessProject>('/api/assessments', { method: 'POST', body: JSON.stringify(body) }),
  detail: (id: number) => request<AssessmentDetail>(`/api/assessments/${id}`),
  addObject: (id: number, body: { layer: string; name: string; description: string; measures: CryptoMeasures }) =>
    request<ObjectView>(`/api/assessments/${id}/objects`, { method: 'POST', body: JSON.stringify(body) }),
  analyze: (id: number) => request<AssessProject>(`/api/assessments/${id}/analyze`, { method: 'POST' }),
  review: (id: number, fid: number, body: ReviewBody) =>
    request<AssessFinding>(`/api/assessments/${id}/findings/${fid}`, { method: 'PATCH', body: JSON.stringify(body) }),
  confirm: (id: number) => request<AssessProject>(`/api/assessments/${id}/confirm`, { method: 'POST' }),
  reportUrl: (id: number) => `/api/assessments/${id}/report.docx`,
}
