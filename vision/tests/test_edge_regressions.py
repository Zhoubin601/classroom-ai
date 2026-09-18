"""Hardware-free regression tests for registration return values and monitor health."""
import ast
import io
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

ROOT = Path(__file__).resolve().parents[1]


def load_definition(file, name, scope):
    tree = ast.parse(file.read_text(encoding="utf-8-sig"))
    definition = next(item for item in tree.body if getattr(item, "name", None) == name)
    exec(compile(ast.Module(body=[definition], type_ignores=[]), str(file), "exec"), scope)
    return scope[name]


class RegistrationTests(unittest.TestCase):
    def exercise(self, filename, *, exists=True, frame=None, detected=None, synced=True):
        scope = {
            "os": SimpleNamespace(path=SimpleNamespace(exists=lambda _: exists)),
            "cv2": SimpleNamespace(imread=lambda _: frame),
            "save_snapshot": Mock(return_value="/uploads/test.jpg"),
            "save_local": Mock(), "sync_to_backend": Mock(return_value=synced),
        }
        function = load_definition(ROOT / filename, "register_from_image", scope)
        app = SimpleNamespace(get=lambda _: detected or [])
        args = SimpleNamespace(id="TEST", name="Test", class_name="Test", gender="UNKNOWN", backend="http://localhost")
        return function(app, "test.jpg", args), scope

    def test_missing_unreadable_or_faceless_image_is_failure(self):
        for filename in ["face_register.py"]:
            for options in [{"exists": False}, {}, {"frame": object()}]:
                with self.subTest(filename=filename, options=options):
                    result, scope = self.exercise(filename, **options)
                    self.assertIs(result, False)
                    scope["sync_to_backend"].assert_not_called()

    def test_backend_failure_is_not_registration_success(self):
        face = SimpleNamespace(embedding=[1.0] * 512, bbox=SimpleNamespace(astype=lambda _: [0, 0, 10, 10]))
        for filename in ["face_register.py"]:
            for synced in [False, True]:
                with self.subTest(filename=filename, synced=synced):
                    result, _ = self.exercise(filename, frame=object(), detected=[face], synced=synced)
                    self.assertEqual(result, synced)


class MonitorTests(unittest.TestCase):
    def test_health_requires_running_monitor_and_actual_frame(self):
        for filename in ["classroom_monitor.py"]:
            for running, frame, expected in [(True, None, False), (True, b"JPEG", True), (False, b"JPEG", False)]:
                with self.subTest(filename=filename, running=running, frame=frame):
                    scope = {"BaseHTTPRequestHandler": object, "json": json,
                             "latest_jpeg_frame": frame, "monitor_instance": SimpleNamespace(running=running)}
                    handler = load_definition(ROOT / filename, "MJPEGHandler", scope)()
                    handler.path = "/health"
                    handler.send_response = Mock()
                    handler.send_header = Mock()
                    handler.end_headers = Mock()
                    handler.wfile = io.BytesIO()
                    handler.do_GET()
                    self.assertEqual(json.loads(handler.wfile.getvalue())["running"], expected)


if __name__ == "__main__":
    unittest.main()
