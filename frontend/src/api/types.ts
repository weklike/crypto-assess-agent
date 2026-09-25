export type SearchMode = 'bm25' | 'dense' | 'hybrid' | 'hybrid_rerank'

export interface SearchRequest {
  query: string
  mode: SearchMode
  k?: number
  layer?: string | null
  level?: number | null
}

export interface SearchHit {
  clauseRef: string
  title: string
  path: string
  layer: string | null
  snippet: string
  bm25Rank: number | null
  bm25Score: number | null
  denseRank: number | null
  denseScore: number | null
  rrfScore: number | null
  rerankScore: number | null
}

export interface SearchResponse {
  mode: SearchMode
  query: string
  k: number
  hits: SearchHit[]
  timings: Record<string, number>
}

export interface Clause {
  clauseRef: string
  docCode: string
  clauseNo: string
  path: string
  title: string
  body: string
  layer: string | null
  levels: string | null
  clauseType: string | null
}

/** 条款分页浏览（GET /api/kb/clauses/page） */
export interface ClausePage {
  items: Clause[]
  total: number
  page: number
  size: number
}

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  [key: string]: unknown
}

export interface QaRequest {
  question: string
  mode?: 'hybrid' | 'hybrid_rerank'
  layer?: string | null
  level?: number | null
}

export interface QaMeta {
  mode: string
  refuseThreshold: number
  topScore: number | null
  hits: { clauseRef: string; title: string; path: string; score: number | null }[]
  timings: Record<string, number>
}

export interface QaCitations {
  valid: { clauseRef: string; title: string }[]
  invalid: { clauseRef: string; reason: 'NOT_FOUND' | 'NOT_IN_RESULTS' }[]
  invalidCount: number
}

export interface QaDone {
  qaRecordId: number
  refused: boolean
  latencyMs: number
  firstTokenMs: number | null
  model: string | null
  inputTokens: number | null
  outputTokens: number | null
  /** 回答混入的 Markdown 标记种类（heading、bold、bullet、code、quote、table、link），空表示纯文本 */
  formatIssues?: string[]
}

export const LAYERS = ['物理和环境', '网络和通信', '设备和计算', '应用和数据', '管理制度', '人员管理', '建设运行', '应急处置']
