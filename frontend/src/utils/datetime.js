export function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  const text = `${value}`.trim().replace('T', ' ')
  const normalized = text.replace(/\.\d+$/, '')
  return normalized.length > 19 ? normalized.slice(0, 19) : normalized
}
