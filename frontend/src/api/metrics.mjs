export function attendanceMetrics(overview, expected) {
  const total = expected ?? overview.totalRegistered
  return { ...overview, totalRegistered: total,
    currentAbsent: Math.max(0, total - overview.currentPresent),
    attendanceRate: total > 0 ? Math.round(overview.currentPresent / total * 1000) / 10 : 0 }
}

export function lookupRatio(lookup, detected) {
  return detected > 0 ? Math.max(0, Math.min(1, lookup / detected)) : 0
}
