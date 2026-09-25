/**
 * 模型回答按纯文本显示：去掉混入的 Markdown 标记（标题井号、加粗、列表符号、反引号、引用符号、链接语法），
 * 保留文字、编号分点和条款引用。是否混入过标记以后端 done 事件的 formatIssues 为准，页面会提示。
 */
export function plainText(text: string): string {
  return text
    .replace(/^\s{0,3}#{1,6}\s+/gm, '')
    .replace(/\*\*([^*\n]+)\*\*/g, '$1')
    .replace(/__([^_\n]+)__/g, '$1')
    .replace(/^(\s*)[-*+]\s+(?=\S)/gm, '$1• ')
    .replace(/`+/g, '')
    .replace(/^\s*>\s?/gm, '')
    .replace(/\[([^\]\n]+)\]\(([^)\s]+)\)/g, '$1')
}
