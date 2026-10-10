import fs from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { Readable } from 'node:stream'

export function validateRegistration(data) {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(data.studentId || '') || !data.name?.trim()) {
    throw Object.assign(new Error('请填写有效学号与姓名，学号仅支持字母、数字、下划线和连字符'), { status: 400 })
  }
}

export function safeUploadPath(root, url) {
  const decoded = decodeURIComponent(url.split('?')[0])
  const base = path.resolve(root, 'runtime', 'uploads')
  const file = path.resolve(base, '.' + decoded)
  const relative = path.relative(base, file)
  if (!relative || relative.startsWith('..') || path.isAbsolute(relative) || !/\.(jpg|jpeg|png|webp)$/i.test(file)) return null
  return file
}

async function readJson(req) {
  let size = 0
  const parts = []
  for await (const part of req) {
    size += part.length
    if (size > 10 * 1024 * 1024) throw Object.assign(new Error('图片请求超过10MB'), { status: 413 })
    parts.push(part)
  }
  try { return JSON.parse(Buffer.concat(parts).toString() || '{}') }
  catch { throw Object.assign(new Error('JSON格式错误'), { status: 400 }) }
}

export function runPython(executable, args, options, timeoutMs = 110000) {
  return new Promise((resolve, reject) => {
    const child = spawn(executable, args, { ...options, windowsHide: true })
    let output = ''
    const timer = setTimeout(() => { child.kill(); reject(new Error('人脸提取超时，请重试')) }, timeoutMs)
    const capture = data => { output = (output + data.toString()).slice(-2000) }
    child.stdout.on('data', capture)
    child.stderr.on('data', capture)
    child.once('error', error => { clearTimeout(timer); reject(error) })
    child.once('close', code => {
      clearTimeout(timer)
      if (code === 0) resolve(output)
      else reject(new Error(output.includes('未在图片中检测到有效人脸') ? '未检测到有效人脸，请正对镜头' : '人脸提取或后端同步失败，请检查Python环境和后端服务'))
    })
  })
}

// Vite handles these local routes before the Java proxy, so it must enforce
// the same face-management role using the backend's verified identity.
export async function authorizeFaceRequest(req) {
  const backend = process.env.CLASSROOM_API_PROXY || 'http://127.0.0.1:8080'
  const headers = {}
  if (req.headers?.authorization) headers.Authorization = req.headers.authorization
  if (req.headers?.cookie) headers.Cookie = req.headers.cookie
  const response = await fetch(backend + '/api/v1/auth/me', { headers, signal: AbortSignal.timeout(5000) })
  if (!response.ok) throw Object.assign(new Error('未登录或会话已过期'), { status: response.status === 401 ? 401 : 403 })
  const body = await response.json()
  if (body.code !== 200 || body.data?.role !== 'DIRECTOR') throw Object.assign(new Error('仅教研室主任可管理人脸底库'), { status: 403 })
  return { ...optionsForFace(req), backend }
}

function optionsForFace(req) {
  // Credentials stay in the child environment, never arguments or config files.
  return { env: { ...process.env, CLASSROOM_FACE_AUTHORIZATION: req.headers?.authorization || '', CLASSROOM_FACE_COOKIE: req.headers?.cookie || '' } }
}

export async function authorizeMonitorRequest(req, data, requireContext = true) {
  const backend = process.env.CLASSROOM_API_PROXY || 'http://127.0.0.1:8080'
  const headers = { ...(req.headers?.authorization ? { Authorization: req.headers.authorization } : {}), ...(req.headers?.cookie ? { Cookie: req.headers.cookie } : {}) }
  const identity = await fetch(backend + '/api/v1/auth/me', { headers, signal: AbortSignal.timeout(5000) })
  if (!identity.ok) throw Object.assign(new Error('登录已过期，请重新登录'), { status: identity.status === 401 ? 401 : 403 })
  const user = (await identity.json()).data
  if (!user || !['DIRECTOR', 'TEACHER', 'SUPERVISOR'].includes(user.role)) throw Object.assign(new Error('当前角色不能使用摄像头考勤'), { status: 403 })
  if (!requireContext) return { user, backend, headers }
  if (!Number.isSafeInteger(data.offeringId) || data.offeringId <= 0 || !Number.isSafeInteger(data.sessionId) || data.sessionId <= 0)
    throw Object.assign(new Error('请选择班次并开始有效考勤会话'), { status: 400 })
  const response = await fetch(`${backend}/api/visual/monitor-context?offeringId=${data.offeringId}&sessionId=${data.sessionId}`, { headers, signal: AbortSignal.timeout(5000) })
  const body = await response.json()
  if (!response.ok || body.code !== 200) throw Object.assign(new Error(body.message || '班次不可开展摄像头考勤'), { status: response.ok ? body.code : response.status })
  return { user, backend, headers, context: body.data }
}

export default function cameraLauncherPlugin(projectRoot) {
  let monitor = null
  let lastMonitorError = ''
  let monitorContext = null
  let launchPending = false
  const monitorPort = Number(process.env.CLASSROOM_MONITOR_PORT || 8088)
  const controlSecret = randomUUID()
  const controlHeaders = { 'X-Monitor-Control': controlSecret }
  const videoUrl = () => monitorContext ? '/api/visual/video-feed?ticket=' + monitorContext.videoToken : null
  const python = process.env.CLASSROOM_PYTHON || path.join(projectRoot, '.venv1', process.platform === 'win32' ? 'Scripts/python.exe' : 'bin/python')
  const options = { cwd: projectRoot, windowsHide: true }
  const visionRoot = path.join(projectRoot, 'vision')
  const health = async () => {
    try {
      const response = await fetch(`http://127.0.0.1:${monitorPort}/health`, { headers: controlHeaders, signal: AbortSignal.timeout(1000) })
      return response.ok ? await response.json() : { running: false }
    } catch { return { running: false } }
  }
  const stop = async () => {
    const child = monitor
    try { await fetch(`http://127.0.0.1:${monitorPort}/stop`, { headers: controlHeaders, signal: AbortSignal.timeout(1500) }) } catch {}
    if (child && child.exitCode === null) {
      await Promise.race([new Promise(resolve => child.once('exit', resolve)), new Promise(resolve => setTimeout(resolve, 6500))])
      if (child.exitCode === null) child.kill()
    }
    if (monitor === child) { monitor = null; monitorContext = null }
  }
  const configureServer = server => {
    server.httpServer?.once('close', () => { monitor?.kill(); monitor = null; monitorContext = null })
    server.middlewares.use('/uploads', (req, res, next) => {
      try {
        const file = safeUploadPath(projectRoot, req.url || '')
        if (!file || !fs.existsSync(file) || !fs.statSync(file).isFile()) return next()
        res.setHeader('Content-Type', { '.png': 'image/png', '.webp': 'image/webp' }[path.extname(file).toLowerCase()] || 'image/jpeg')
        fs.createReadStream(file).on('error', () => res.destroy()).pipe(res)
      } catch { res.statusCode = 400; res.end('Invalid path') }
    })
    // Disk files must never be removed before a backend delete has succeeded.
    const routes = new Map([
      ['/api/face/register-webcam', 'POST'], ['/api/face/launch-register', 'POST'],
      ['/api/face/verify-webcam', 'POST'], ['/api/face/launch-verify', 'POST'],
      ['/api/visual/start-monitor', 'POST'], ['/api/visual/stop-monitor', 'POST'],
      ['/api/visual/monitor-status', 'GET'], ['/api/visual/video-feed', 'GET']
    ])
    server.middlewares.use(async (req, res, next) => {
      const route = (req.url || '').split('?')[0]
      if (!routes.has(route)) return next()
      const send = (code, message, data = null) => {
        if (res.writableEnded || res.destroyed) return
        res.statusCode = code
        res.setHeader('Content-Type', 'application/json;charset=utf-8')
        res.end(JSON.stringify({ code, message, data, timestamp: Date.now() }))
      }
      if (req.method !== routes.get(route)) { res.setHeader('Allow', routes.get(route)); return send(405, '请求方法不支持') }
      try {
        const face = route.startsWith('/api/face/') ? await authorizeFaceRequest(req) : null
        const faceOptions = face ? { ...options, env: face.env } : options
        if (route === '/api/visual/video-feed') {
          const ticket = new URL(req.url, 'http://localhost').searchParams.get('ticket')
          if (!monitorContext || !ticket || ticket !== monitorContext.videoToken) return send(401, '视频授权已失效，请重新启动摄像头')
          await authorizeMonitorRequest({ headers: monitorContext.headers }, monitorContext)
          const abort = new AbortController()
          res.on('close', () => abort.abort())
          const upstream = await fetch(`http://127.0.0.1:${monitorPort}/video_feed`, { headers: controlHeaders, signal: abort.signal })
          if (!upstream.ok) return send(502, '摄像头画面暂不可用')
          res.setHeader('Content-Type', upstream.headers.get('content-type'))
          res.setHeader('Cache-Control', 'no-store')
          Readable.fromWeb(upstream.body).on('error', () => res.destroy()).pipe(res)
          return
        }
        if (route.startsWith('/api/visual/')) {
          const identity = await authorizeMonitorRequest(req, {}, false)
          if (monitorContext && identity.user.id !== monitorContext.userId) return send(403, '摄像头正由其他登录用户使用')
          if (route === '/api/visual/monitor-status') {
            const current = await health()
            return send(200, 'success', { ...current, running: !!monitor && !!current.running,
              starting: !!monitor && !current.running && !current.fatal,
              offeringId: monitorContext?.offeringId ?? null, sessionId: monitorContext?.sessionId ?? null,
              videoUrl: videoUrl(), error: current.error || lastMonitorError || null })
          }
          if (route === '/api/visual/stop-monitor') {
            await stop(); lastMonitorError = ''
            return send(200, '摄像头已停止', { running: false })
          }
          if (route === '/api/visual/start-monitor') {
            const data = await readJson(req)
            const verified = await authorizeMonitorRequest(req, data)
            if (launchPending) return send(409, '摄像头正在初始化，请等待')
            if (monitor) {
              if (monitorContext.offeringId !== data.offeringId || monitorContext.sessionId !== data.sessionId) return send(409, '请先停止当前班次的摄像头')
              const current = await health()
              return send(200, '摄像头正在运行或初始化', { ...current, starting: !current.running, videoUrl: videoUrl() })
            }
            launchPending = true
            try {
              lastMonitorError = ''
              monitorContext = { ...data, userId: verified.user.id, headers: req.headers,
                videoToken: randomUUID() }
              const logFile = path.join(projectRoot, 'runtime', 'logs', 'monitor_spawn.log')
              fs.mkdirSync(path.dirname(logFile), { recursive: true })
              const child = spawn(python, [path.join(visionRoot, 'classroom_monitor.py'), '--backend', verified.backend, '--port', String(monitorPort), '--no-window'], {
                ...options, env: { ...process.env,
                  CLASSROOM_MONITOR_AUTHORIZATION: req.headers?.authorization || '', CLASSROOM_MONITOR_COOKIE: req.headers?.cookie || '',
                  CLASSROOM_MONITOR_OFFERING_ID: String(data.offeringId), CLASSROOM_MONITOR_SESSION_ID: String(data.sessionId),
                  CLASSROOM_MONITOR_CONTROL_SECRET: controlSecret }
              })
              monitor = child
              let recentOutput = ''
              const capture = d => {
                const clean = d.toString().replace(/Bearer\s+[^\s"']+/gi, 'Bearer [REDACTED]')
                recentOutput = (recentOutput + clean).slice(-1500)
                try { fs.appendFileSync(logFile, clean) } catch {}
              }
              child.stdout?.on('data', capture); child.stderr?.on('data', capture)
              child.once('exit', (code) => {
                if (monitor === child) {
                  monitor = null; monitorContext = null
                  lastMonitorError = code === 0 ? '摄像头已结束' : (recentOutput.trim().split('\n').at(-1)?.slice(0, 300) || '视觉进程启动失败，请检查摄像头和Python依赖')
                }
              })
              await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', error => { if (monitor === child) { monitor = null; monitorContext = null }; reject(error) }) })
              return send(200, '正在初始化摄像头并连接考勤数据', { running: false, starting: true, videoUrl: videoUrl() })
            } finally { launchPending = false }
          }
        }
        const data = await readJson(req)

        if (route === '/api/face/launch-verify') {
          const child = spawn(python, [path.join(visionRoot, 'face_verify.py'), '--backend', face.backend], { ...faceOptions, stdio: 'ignore' })
          await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject) })
          return send(200, '独立桌面窗口实时识别程序已启动，请查看弹出窗口')
        }

        if (route === '/api/face/verify-webcam') {
          if (typeof data.imageBase64 !== 'string' || !/^data:image\/(jpeg|png);base64,[A-Za-z0-9+/=]+$/.test(data.imageBase64)) {
            return send(400, '未收到有效PNG/JPEG画面数据')
          }
          const threshold = typeof data.threshold === 'number' ? data.threshold : 0.45
          const tempDir = path.join(projectRoot, 'runtime', 'uploads', 'temp')
          fs.mkdirSync(tempDir, { recursive: true })
          const tempFile = path.join(tempDir, `verify_webcam_${randomUUID()}.jpg`)
          try {
            fs.writeFileSync(tempFile, Buffer.from(data.imageBase64.split(',')[1], 'base64'))
            const output = await runPython(python, [
              path.join(visionRoot, 'face_verify.py'),
              '--backend', face.backend,
              '--image', tempFile,
              '--threshold', String(threshold)
            ], faceOptions)
            const lines = output.trim().split(/\r?\n/)
            const jsonLine = lines.reverse().find(l => l.trim().startsWith('{') && l.trim().endsWith('}'))
            if (!jsonLine) throw new Error('未获取到人脸识别有效分析结果')
            const resObj = JSON.parse(jsonLine)
            return send(resObj.code || 200, resObj.message || (resObj.matched ? '成功识别学生身份' : '未达到判定阈值 (Unknown)'), resObj)
          } finally {
            fs.rmSync(tempFile, { force: true })
          }
        }

        validateRegistration(data)
        const args = [path.join(visionRoot, 'face_register.py'), '--backend', face.backend, '--id', data.studentId, '--name', data.name,
          '--class-name', data.className || '', '--gender', data.gender || 'UNKNOWN']
        if (route === '/api/face/launch-register') {
          const child = spawn(python, args, { ...faceOptions, stdio: 'ignore' })
          await new Promise((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject) })
          return send(200, '摄像头注册程序已启动，请等待取景窗口')
        }
        if (typeof data.imageBase64 !== 'string' || !/^data:image\/(jpeg|png);base64,[A-Za-z0-9+/=]+$/.test(data.imageBase64)) {
          return send(400, '未收到有效PNG/JPEG画面数据')
        }
        const tempDir = path.join(projectRoot, 'runtime', 'uploads', 'temp')
        fs.mkdirSync(tempDir, { recursive: true })
        const tempFile = path.join(tempDir, `webcam_${randomUUID()}.jpg`)
        try {
          fs.writeFileSync(tempFile, Buffer.from(data.imageBase64.split(',')[1], 'base64'))
          await runPython(python, [...args, '--image', tempFile], faceOptions)
          return send(200, '人脸已录入并同步后端', { studentId: data.studentId, avatarUrl: `/uploads/faces/${data.studentId}_snapshot.jpg` })
        } finally { fs.rmSync(tempFile, { force: true }) }
      } catch (error) { send(error.status || 500, error.message || '操作失败') }
    })
  }
  return { name: 'camera-launcher-plugin', configureServer, configurePreviewServer: configureServer }
}
