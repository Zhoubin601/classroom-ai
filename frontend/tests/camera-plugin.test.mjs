import test from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import fs from 'node:fs'
import os from 'node:os'
import { Readable } from 'node:stream'
import cameraLauncherPlugin from '../dev/camera-plugin.mjs'
import { validateRegistration, safeUploadPath, runPython } from '../dev/camera-plugin.mjs'

test('student id must not escape file directories', () => {
  assert.throws(() => validateRegistration({ studentId: '../../x', name: 'test' }))
  assert.throws(() => validateRegistration({ studentId: 'S01', name: ' ' }))
  assert.doesNotThrow(() => validateRegistration({ studentId: 'S01_2', name: '测试' }))
})
test('upload route prevents traversal and non-image reads', () => {
  const root = path.resolve('.')
  assert.equal(safeUploadPath(root, '/../package.json'), null)
  assert.equal(safeUploadPath(root, '/%2e%2e/secret.jpg'), null)
  assert.equal(safeUploadPath(root, '/faces/test.jpg?x=1'), path.join(root, 'runtime/uploads/faces/test.jpg'))
})
test('missing executable rejects without crashing the dev server', async () => {
  await assert.rejects(runPython('nonexistent-python-test-90538', [], {}), /ENOENT/)
})
test('nonzero extraction exit is failure, zero is success', async () => {
  await assert.rejects(runPython(process.execPath, ['-e', 'process.exit(1)'], {}), /失败/)
  await assert.doesNotReject(runPython(process.execPath, ['-e', 'process.exit(0)'], {}))
})

test('webcam verification launches vision script and cleans its shared upload', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'classroom layout '))
  const previousPython = process.env.CLASSROOM_PYTHON
  try {
    fs.mkdirSync(path.join(root, 'vision'))
    // A harmless Node fixture stands in for Python: no camera, model or API access.
    fs.writeFileSync(path.join(root, 'vision', 'face_verify.py'), `
      const fs = require('node:fs');
      const file = process.argv[process.argv.indexOf('--image') + 1];
      console.log(JSON.stringify({code: 200, matched: true, imageExists: fs.existsSync(file), cwd: process.cwd()}));
    `)
    process.env.CLASSROOM_PYTHON = process.execPath
    const handlers = []
    cameraLauncherPlugin(root).configureServer({
      httpServer: { once() {} },
      middlewares: { use(...args) { if (args.length === 1) handlers.push(args[0]) } },
    })
    const req = Readable.from([Buffer.from(JSON.stringify({ imageBase64: 'data:image/jpeg;base64,YQ==' }))])
    req.url = '/api/face/verify-webcam'
    req.method = 'POST'
    let result
    const res = { setHeader() {}, end(body) { result = JSON.parse(body) } }
    await handlers[0](req, res, () => assert.fail('Route was not handled'))
    assert.equal(res.statusCode, 200, result.message)
    assert.equal(result.data.imageExists, true)
    assert.equal(result.data.cwd, root)
    assert.deepEqual(fs.readdirSync(path.join(root, 'runtime', 'uploads', 'temp')), [])
  } finally {
    if (previousPython === undefined) delete process.env.CLASSROOM_PYTHON
    else process.env.CLASSROOM_PYTHON = previousPython
    fs.rmSync(root, { recursive: true, force: true })
  }
})
