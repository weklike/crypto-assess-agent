import type { AssessFinding } from '../api/assessments'

export interface FindingFilter {
  objectId?: number | null
  judgment?: string | null
  gapsOnly?: boolean
  unreviewedOnly?: boolean
}

const GAPS = new Set(['不符合', '部分符合'])

export function filterFindings(findings: AssessFinding[], filter: FindingFilter): AssessFinding[] {
  return findings.filter(
    (f) =>
      (filter.objectId == null || f.objectId === filter.objectId) &&
      (!filter.judgment || f.judgment === filter.judgment) &&
      (!filter.gapsOnly || GAPS.has(f.judgment)) &&
      (!filter.unreviewedOnly || !f.reviewed),
  )
}

export function summarize(findings: AssessFinding[]) {
  return {
    total: findings.length,
    reviewed: findings.filter((f) => f.reviewed).length,
    gaps: findings.filter((f) => GAPS.has(f.judgment)).length,
  }
}

/** 按 D/A/K 评分的技术层面（量化评估规则 2023 版），其余为管理层面，直接判定。 */
export const TECHNICAL_LAYERS = ['物理和环境', '网络和通信', '设备和计算', '应用和数据']

/** 表 2：Ra 按算法安全强度取值（≥112 比特 1，80–111 比特 0.5，<80 比特 0.2）。 */
export const RA_OPTIONS = [1, 0.5, 0.2]

export function isTechnicalLayer(layer: string | undefined): boolean {
  return layer != null && TECHNICAL_LAYERS.includes(layer)
}

/** 表 3：第三级 1.2、第四级 1.5（需满足对应密码模块等级与其他密钥管理要求），否则 1。 */
export function rkOptions(level: number): number[] {
  if (level === 3) return [1, 1.2]
  if (level === 4) return [1, 1.5]
  return [1]
}

const flag = (v: boolean | null) => (v == null ? '/' : v ? '√' : '×')

/** 与报告一致的简写，如“D√ A× K√ Ra=0.5”。 */
export function dimensionsText(f: AssessFinding): string {
  if (f.dimD == null) return ''
  if (!f.dimD) return 'D×'
  let text = `D√ A${flag(f.dimA)} K${flag(f.dimK)}`
  if (f.dimA === false && f.ra != null) text += ` Ra=${f.ra}`
  if (f.dimK === false && f.rk != null) text += ` Rk=${f.rk}`
  return text
}

/** 评分还缺的参数：A 不满足缺 Ra、K 不满足缺 Rk；确认时后端会拒绝。 */
export function needsParameter(f: AssessFinding): 'Ra' | 'Rk' | null {
  if (f.dimD !== true) return null
  if (f.dimA === false && f.ra == null) return 'Ra'
  if (f.dimK === false && f.rk == null) return 'Rk'
  return null
}
