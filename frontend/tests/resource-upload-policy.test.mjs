import test from 'node:test'
import assert from 'node:assert/strict'
import { parseResourceUploadPolicy, validateResourceUpload, formatUploadLimit } from '../src/ui/resourceUploadPolicy.mjs'

test('increased policy permits a 101 MiB file and includes its exact upper boundary', () => {
  const max = 150 * 1024 * 1024
  const policy = parseResourceUploadPolicy({ maxFileBytes: max, allowedExtensions: [' PDF ', '.pdf', 'DocX'] })
  assert.deepEqual(policy.allowedExtensions, ['pdf', 'docx'])
  assert.equal(validateResourceUpload({ name: 'lesson.PDF', size: 101 * 1024 * 1024 }, policy), '')
  assert.equal(validateResourceUpload({ name: ' lesson.PDF ', size: 1 }, policy), '')
  assert.equal(validateResourceUpload({ name: 'lesson.pdf', size: max }, policy), '')
  assert.match(validateResourceUpload({ name: 'lesson.pdf', size: max + 1 }, policy), /157286400 字节/)
})
test('lowered PDF-only policy rejects wrong formats and one-byte overflow', () => {
  const policy = parseResourceUploadPolicy({ maxFileBytes: 1024, allowedExtensions: ['pdf'] })
  assert.equal(validateResourceUpload({ name: 'lesson.pdf', size: 1024 }, policy), '')
  assert.match(validateResourceUpload({ name: 'lesson.pdf', size: 1025 }, policy), /1024 字节/)
  assert.match(validateResourceUpload({ name: 'lesson.docx', size: 512 }, policy), /允许：PDF/)
  assert.match(validateResourceUpload({ name: 'lesson.pdf', size: 0 }, policy), /非空/)
})
test('missing or malformed policy cannot silently fall back to a fixed limit', () => {
  for (const value of [null, [], { maxFileBytes: '1024', allowedExtensions: ['pdf'] }, { maxFileBytes: -1, allowedExtensions: ['pdf'] }, { maxFileBytes: 1024, allowedExtensions: ['*'] }]) {
    assert.throws(() => parseResourceUploadPolicy(value), /规则无效/)
  }
  assert.match(validateResourceUpload({ name: 'lesson.pdf', size: 1 }, null), /尚未就绪/)
})
test('limit label preserves exact byte values for small configured limits', () => {
  assert.equal(formatUploadLimit(1024), '1 KiB（1024 字节）')
  assert.equal(formatUploadLimit(512), '512 字节')
})
