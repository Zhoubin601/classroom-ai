import test from 'node:test'
import assert from 'node:assert/strict'
import { attendanceMetrics, lookupRatio } from '../src/api/metrics.mjs'

test('lookup stream uses a fraction, including empty classrooms', () => {
  assert.equal(lookupRatio(9, 10), 0.9)
  assert.equal(lookupRatio(0, 0), 0)
  assert.equal(lookupRatio(11, 10), 1)
})
test('selected offering recalculates absence and attendance together', () => {
  const result = attendanceMetrics({ totalRegistered: 4, currentPresent: 4 }, 95)
  assert.equal(result.currentAbsent, 91)
  assert.equal(result.attendanceRate, 4.2)
  assert.equal(attendanceMetrics({ currentPresent: 0 }, 0).totalRegistered, 0)
  assert.equal(attendanceMetrics({ currentPresent: 4 }, 0).currentAbsent, 0)
})
