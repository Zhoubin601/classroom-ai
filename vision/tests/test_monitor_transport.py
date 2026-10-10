"""Actual loopback HTTP, without cameras, GPUs, saved credentials or face vectors."""
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from vision.monitor_transport import MonitorTransport


class TransportTests(unittest.TestCase):
    def setUp(self):
        self.requests = []
        self.reject_code = None
        outer = self
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self): self.handle_request()
            def do_POST(self): self.handle_request()
            def handle_request(self):
                body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or '{}')
                outer.requests.append((self.path, self.headers.get('Authorization'), body))
                code = outer.reject_code or (200 if self.headers.get('Authorization') == 'Bearer test-only' else 401)
                data = {'offeringId': 2, 'sessionId': 7, 'courseName': '合成课程', 'className': '合成班次', 'faces': []} if self.command == 'GET' else 'OK'
                self.send_response(code); self.send_header('Content-Type', 'application/json'); self.end_headers()
                self.wfile.write(json.dumps({'code': code, 'data': data}).encode())
            def log_message(self, *args): pass
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True); self.thread.start()
        self.transport = MonitorTransport(f'http://127.0.0.1:{self.server.server_port}', 2, 7, 'Bearer test-only', '')
    def tearDown(self): self.server.shutdown(); self.server.server_close(); self.thread.join()

    def test_real_http_carries_authentication_and_server_validated_context(self):
        self.assertTrue(self.transport.send({'offeringId': 99, 'attendanceSessionId': 100, 'lookupRate': .5}))
        self.assertEqual(len(self.requests), 2)
        self.assertTrue(all(r[1] == 'Bearer test-only' for r in self.requests))
        frame = self.requests[-1][2]
        self.assertEqual((frame['offeringId'], frame['attendanceSessionId']), (2, 7))
        self.assertEqual(frame['className'], '合成班次')
        self.assertTrue(self.transport.status()['reporting'])
        self.assertNotIn('authorization', self.transport.status())

    def test_401_403_409_stop_transport_and_expose_safe_actionable_error(self):
        for code in (401, 403, 409):
            with self.subTest(code=code):
                transport = MonitorTransport(self.transport.backend, 2, 7, 'Bearer test-only', '')
                self.reject_code = code
                self.assertFalse(transport.send({'lookupRate': 0}))
                self.assertTrue(transport.fatal)
                self.assertIn(str(code), transport.status()['error'])
                count = len(self.requests)
                self.assertFalse(transport.send({}))
                self.assertEqual(count, len(self.requests))
                self.assertNotIn('test-only', transport.status()['error'])

    def test_transient_failure_recovers_but_three_failures_stop(self):
        self.reject_code = 500
        self.assertFalse(self.transport.send({})); self.assertFalse(self.transport.fatal)
        self.reject_code = None
        self.assertTrue(self.transport.send({})); self.assertEqual(self.transport.failures, 0)
        self.reject_code = 500
        for _ in range(3): self.assertFalse(self.transport.send({}))
        self.assertTrue(self.transport.fatal)

    def test_missing_auth_or_offering_never_makes_request(self):
        with self.assertRaises(ValueError): MonitorTransport(self.transport.backend, 2, 7, '', '')
        with self.assertRaises(ValueError): MonitorTransport(self.transport.backend, 0, 7, 'Bearer test-only', '')
        self.assertEqual(self.requests, [])
