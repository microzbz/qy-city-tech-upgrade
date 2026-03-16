export function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return `${value}`.replace('T', ' ')
}
