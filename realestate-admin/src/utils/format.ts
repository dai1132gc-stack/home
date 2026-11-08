/** 浏览器端 Blob 预览与下载（合同 PDF 需鉴权，不能直接用 URL） */
export function openBlobInNewTab(blob: Blob): void {
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener')
  // 新标签加载后再释放，避免立即 revoke 导致空白页
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/** 后端时间统一 UTC ISO，前端按本地时区展示 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  return value.length >= 10 ? value.slice(0, 10) : value
}

export function formatMoney(value: number | null | undefined): string {
  return `¥${Number(value ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}
