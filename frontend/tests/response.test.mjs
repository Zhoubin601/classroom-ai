import test from 'node:test'
import assert from 'node:assert/strict'
import { checkApiResponse, rejectApiError } from '../src/api/response.mjs'

test('HTTP 200 business failures reject and preserve backend details', () => {
  const response = { status: 200, data: { code: 400, message: '排课冲突' } }
  assert.throws(() => checkApiResponse(response), error => error.message === '排课冲突' && error.response === response)
})
test('successful empty responses remain valid', () => {
  const response = { data: { code: 200, data: null } }
  assert.equal(checkApiResponse(response), response)
})
test('HTTP errors preserve response metadata and readable business message', async () => {
  const error = Object.assign(new Error('Request failed'), { response: { data: { message: '大纲已锁定' } } })
  await assert.rejects(rejectApiError(error), result => result === error && result.message === '大纲已锁定')
})
