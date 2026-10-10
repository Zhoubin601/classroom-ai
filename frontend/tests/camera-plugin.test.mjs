import test from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import fs from 'node:fs'
import os from 'node:os'
import { Readable } from 'node:stream'
import cameraLauncherPlugin from '../dev/camera-plugin.mjs'
import { validateRegistration, safeUploadPath, runPython, authorizeFaceRequest, authorizeMonitorRequest } from '../dev/camera-plugin.mjs'

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
  const previousFetch = globalThis.fetch
  try {
    globalThis.fetch = async () => ({ ok: true, json: async () => ({ code: 200, data: { role: 'DIRECTOR' } }) })
    fs.mkdirSync(path.join(root, 'vision'))
    // A harmless Node fixture stands in for Python: no camera, model or API access.
    fs.writeFileSync(path.join(root, 'vision', 'face_verify.py'), `
      const fs = require('node:fs');
      const file = process.argv[process.argv.indexOf('--image') + 1];
      console.log(JSON.stringify({code: 200, matched: true, imageExists: fs.existsSync(file), cwd: process.cwd(), forwarded: process.env.CLASSROOM_FACE_AUTHORIZATION === 'Bearer synthetic', backendArgument: process.argv.includes('--backend')}));
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
    req.headers = { authorization: 'Bearer synthetic' }
    let result
    const res = { setHeader() {}, end(body) { result = JSON.parse(body) } }
    await handlers[0](req, res, () => assert.fail('Route was not handled'))
    assert.equal(res.statusCode, 200, result.message)
    assert.equal(result.data.imageExists, true)
    assert.equal(result.data.cwd, root)
    assert.equal(result.data.forwarded, true)
    assert.equal(result.data.backendArgument, true)
    assert.deepEqual(fs.readdirSync(path.join(root, 'runtime', 'uploads', 'temp')), [])
  } finally {
    globalThis.fetch = previousFetch
    if (previousPython === undefined) delete process.env.CLASSROOM_PYTHON
    else process.env.CLASSROOM_PYTHON = previousPython
    fs.rmSync(root, { recursive: true, force: true })
  }
})

test('local face adapters verify backend role and reject before spawning or writing', async () => {
  const previousFetch = globalThis.fetch
  try {
    for (const role of ['TEACHER', 'SUPERVISOR']) {
      globalThis.fetch = async () => ({ ok: true, json: async () => ({ code: 200, data: { role } }) })
      await assert.rejects(authorizeFaceRequest({ headers: { authorization: 'Bearer synthetic' } }), e => e.status === 403)
      const handlers = []
      cameraLauncherPlugin(path.join(os.tmpdir(), 'missing-unwritten-test-directory')).configureServer({
        httpServer: { once() {} }, middlewares: { use(...args) { if (args.length === 1) handlers.push(args[0]) } },
      })
      const req = Readable.from([])
      req.url = '/api/face/launch-register'; req.method = 'POST'; req.headers = {}
      let body
      const res = { setHeader() {}, end(value) { body = JSON.parse(value) } }
      await handlers[0](req, res, () => assert.fail('Not handled'))
      assert.equal(res.statusCode, 403); assert.equal(body.code, 403)
    }
    globalThis.fetch = async () => ({ ok: false, status: 401 })
    await assert.rejects(authorizeFaceRequest({ headers: {} }), e => e.status === 401)
  } finally { globalThis.fetch = previousFetch }
})

test('camera adapter forwards auth and validates active offering before starting hardware', async () => {
  const previousFetch = globalThis.fetch
  const requests = []
  try {
    globalThis.fetch = async (url, options) => {
      requests.push({ url, headers: options.headers })
      return { ok: true, json: async () => ({ code: 200, data: url.endsWith('/me') ? { id: 8, role: 'TEACHER' } : { offeringId: 2, sessionId: 7 } }) }
    }
    const verified = await authorizeMonitorRequest({ headers: { authorization: 'Bearer synthetic' } }, { offeringId: 2, sessionId: 7 })
    assert.equal(verified.context.offeringId, 2)
    assert.ok(requests[1].url.endsWith('offeringId=2&sessionId=7'))
    assert.ok(requests.every(r => r.headers.Authorization === 'Bearer synthetic'))
    await assert.rejects(authorizeMonitorRequest({headers:{}}, {offeringId:'2',sessionId:7}), e=>e.status===400)
    for (const code of [401,403,409]) {
      globalThis.fetch = async url => url.endsWith('/me') && code!==401
        ? { ok:true, json:async()=>({code:200,data:{id:8,role:'SUPERVISOR'}}) }
        : { ok:false,status:code,json:async()=>({code,message:'拒绝'}) }
      await assert.rejects(authorizeMonitorRequest({headers:{}}, {offeringId:2,sessionId:7}), e=>e.status===code)
    }
  } finally { globalThis.fetch = previousFetch }
})

test('camera lifecycle endpoints require authentication and never reuse public local video', async () => {
  const previousFetch = globalThis.fetch
  try {
    globalThis.fetch = async () => ({ok:false,status:401})
    const handlers=[]
    cameraLauncherPlugin(path.join(os.tmpdir(),'camera-no-write')).configureServer({httpServer:{once(){}},middlewares:{use(...args){if(args.length===1)handlers.push(args[0])}}})
    for (const [url,method] of [['/api/visual/start-monitor','POST'],['/api/visual/stop-monitor','POST'],['/api/visual/monitor-status','GET'],['/api/visual/video-feed','GET']]) {
      const req=Readable.from([]);Object.assign(req,{url,method,headers:{}})
      let body;const res={setHeader(){},end(text){body=JSON.parse(text)}}
      await handlers[0](req,res,()=>assert.fail('route bypassed'))
      assert.equal(res.statusCode,401);assert.equal(body.code,401)
    }
  }finally{globalThis.fetch=previousFetch}
})
