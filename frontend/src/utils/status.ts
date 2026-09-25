export const STATUS_TEXT: Record<string, string> = {
  DRAFT: '草稿',
  READY: '待分析',
  ANALYZING: '分析中',
  REVIEW: '待复核',
  FAILED: '分析失败',
  CONFIRMED: '已确认',
  SCORED: '已评分',
  REPORTED: '已出报告',
}

export const statusText = (status: string) => STATUS_TEXT[status] ?? status
