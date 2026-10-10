"""Authenticated camera transport. No camera/model imports and no credential logging."""
import json
import os
import threading
import time
import urllib.request
import urllib.error


class MonitorTransport:
    def __init__(self, backend, offering_id, session_id, authorization=None, cookie=None):
        self.backend = backend.rstrip('/')
        self.offering_id = int(offering_id)
        self.session_id = int(session_id)
        if self.offering_id <= 0 or self.session_id <= 0:
            raise ValueError('必须选择有效班次和活动考勤会话')
        self.authorization = authorization if authorization is not None else os.environ.get('CLASSROOM_MONITOR_AUTHORIZATION', '')
        self.cookie = cookie if cookie is not None else os.environ.get('CLASSROOM_MONITOR_COOKIE', '')
        if not self.authorization and not self.cookie:
            raise ValueError('缺少摄像头认证，请登录平台后从考勤大屏启动')
        self.error = None
        self.fatal = False
        self.failures = 0
        self.last_success = None
        self.frames_sent = 0
        self.context = None
        self._lock = threading.Lock()

    def request(self, route, payload=None):
        headers = {'Accept': 'application/json'}
        if self.authorization: headers['Authorization'] = self.authorization
        if self.cookie: headers['Cookie'] = self.cookie
        data = None if payload is None else json.dumps(payload).encode('utf-8')
        if data is not None: headers['Content-Type'] = 'application/json'
        request = urllib.request.Request(self.backend + route, data=data, headers=headers,
                                         method='GET' if data is None else 'POST')
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                body = json.loads(response.read().decode('utf-8'))
            if body.get('code') != 200:
                raise urllib.error.HTTPError(request.full_url, body.get('code', 500), '业务请求失败', {}, None)
            return body.get('data')
        except urllib.error.HTTPError as e:
            reason = {401: '登录已过期，请重新登录后启动摄像头',
                      403: '班次授权已变更，摄像头已停止',
                      409: '班次已冻结或考勤已结束，摄像头已停止',
                      400: '班次或考勤会话不匹配，请重新选择班次'}.get(e.code, '后端处理失败，请重试')
            self.error = f'{reason}（HTTP {e.code}）'
            self.fatal = e.code in (400, 401, 403, 409)
            raise

    def load_context(self):
        context = self.request(f'/api/visual/monitor-context?offeringId={self.offering_id}&sessionId={self.session_id}')
        if context.get('offeringId') != self.offering_id or context.get('sessionId') != self.session_id:
            self.error = '后端返回的班次与考勤会话不一致'; self.fatal = True
            raise ValueError(self.error)
        self.context = context
        return context

    def send(self, metrics):
        if self.fatal or not self._lock.acquire(blocking=False): return False
        try:
            context = self.context or self.load_context()
            payload = {**metrics, 'offeringId': self.offering_id, 'attendanceSessionId': self.session_id,
                       'sessionId': f'CAMERA_{self.offering_id}_{self.session_id}',
                       'courseName': context['courseName'], 'className': context['className']}
            self.request('/api/visual/report/stream', payload)
            self.last_success = time.time(); self.frames_sent += 1; self.failures = 0; self.error = None
            return True
        except Exception:
            self.failures += 1
            if not self.error: self.error = '无法连接后端，正在重试；请检查服务状态'
            if self.failures >= 3: self.fatal = True
            return False
        finally:
            self._lock.release()

    def status(self):
        return {'reporting': bool(self.last_success and time.time() - self.last_success < 12 and not self.fatal),
                'lastReportTime': self.last_success, 'framesSent': self.frames_sent,
                'error': self.error, 'fatal': self.fatal,
                'offeringId': self.offering_id, 'sessionId': self.session_id}
