import fs from 'node:fs'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { randomUUID } from 'node:crypto'

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

export default function cameraLauncherPlugin(projectRoot) {
  let monitor = null
  let lastMonitorError = ''
  const python = process.env.CLASSROOM_PYTHON || path.join(projectRoot, '.venv1', process.platform === 'win32' ? 'Scripts/python.exe' : 'bin/python')
  const options = { cwd: projectRoot, windowsHide: true }
  const visionRoot = path.join(projectRoot, 'vision')
  const health = async () => {
    try {
      const response = await fetch('http://127.0.0.1:8088/health', { signal: AbortSignal.timeout(1000) })
      return response.ok && (await response.json()).running === true
    } catch { return false }
  }
  return { name: 'camera-launcher-plugin', configureServer(server) {
    server.httpServer?.once('close', () => { monitor?.kill(); monitor = null })
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
      ['/api/visual/monitor-status', 'GET']
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
        if (route === '/api/visual/monitor-status') {
          const isRunning = await health()
          return send(200, 'success', {
            running: isRunning,
            starting: !!monitor && !isRunning,
            error: isRunning ? null : (lastMonitorError || null)
          })
        }
        if (route === '/api/visual/stop-monitor') {
          const stopping = monitor
          try { await fetch('http://127.0.0.1:8088/stop', { signal: AbortSignal.timeout(1500) }) } catch {}
          stopping?.kill()
          if (monitor === stopping) monitor = null
          lastMonitorError = ''
          return send(200, '监控停止请求已发送', { running: false })
        }
        if (route === '/api/visual/start-monitor') {
          if (await health()) return send(200, '监控已运行', { running: true })
          if (monitor) return send(200, '推断引擎正在初始化', { running: false, starting: true })
          lastMonitorError = ''
          const logFile = path.join(projectRoot, 'runtime', 'logs', 'monitor_spawn.log')
          let recentOutput = ''
          fs.mkdirSync(path.dirname(logFile), { recursive: true })
          const child = spawn(python, [path.join(visionRoot, 'classroom_monitor.py'), '--port', '8088', '--no-window'], { ...options })
          monitor = child
          child.stdout?.on('data', d => {
            const str = d.toString()
            recentOutput = (recentOutput + str).slice(-2000)
            try { fs.appendFileSync(logFile, `[STDOUT] ${str}`) } catch {}
          })
          child.stderr?.on('data', d => {
            const str = d.toString()
            recentOutput = (recentOutput + str).slice(-2000)
            try { fs.appendFileSync(logFile, `[STDERR] ${str}`) } catch {}
          })
          child.once('error', err => {
            const errStr = err.stack || String(err)
            recentOutput = (recentOutput + '\n' + errStr).slice(-2000)
            try { fs.appendFileSync(logFile, `[ERROR] ${errStr}\n`) } catch {}
            if (monitor === child) {
              monitor = null
              lastMonitorError = err.message || '进程启动失败'
            }
          })
          child.once('exit', (code, signal) => {
            try { fs.appendFileSync(logFile, `[EXIT] code=${code}, signal=${signal}\n`) } catch {}
            if (monitor === child) {
              monitor = null
              if (code !== 0 && code !== null) {
                lastMonitorError = recentOutput.trim() || `视觉进程异常退出 (code: ${code})`
              }
            }
          })
          await new Promise((resolve, reject) => {
            child.once('spawn', resolve)
            child.once('error', error => { if (monitor === child) monitor = null; reject(error) })
          })
          return send(200, '推断引擎已启动，正在初始化模型与摄像头', { running: false, starting: true })
        }
        const data = await readJson(req)

        if (route === '/api/face/launch-verify') {
          const child = spawn(python, [path.join(visionRoot, 'face_verify.py')], { ...options, stdio: 'ignore' })
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
              '--image', tempFile,
              '--threshold', String(threshold)
            ], options)
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
        const args = [path.join(visionRoot, 'face_register.py'), '--id', data.studentId, '--name', data.name,
          '--class-name', data.className || '', '--gender', data.gender || 'UNKNOWN']
        if (route === '/api/face/launch-register') {
          const child = spawn(python, args, { ...options, stdio: 'ignore' })
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
          await runPython(python, [...args, '--image', tempFile], options)
          return send(200, '人脸已录入并同步后端', { studentId: data.studentId, avatarUrl: `/uploads/faces/${data.studentId}_snapshot.jpg` })
        } finally { fs.rmSync(tempFile, { force: true }) }
      } catch (error) { send(error.status || 500, error.message || '操作失败') }
    })
  } }
}
