"""Check launch and resource paths without importing GPU/camera dependencies."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

PROJECT_ROOT = Path(__file__).resolve().parents[2]


class LayoutTests(unittest.TestCase):
    def test_shared_resources_do_not_depend_on_working_directory(self):
        code = (
            "import json, sys; "
            "sys.path.insert(0, sys.argv[1]); "
            "from paths import PROJECT_ROOT, MODELS_DIR, UPLOADS_DIR; "
            "print(json.dumps([str(PROJECT_ROOT), str(MODELS_DIR), str(UPLOADS_DIR)]))"
        )
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(
                [sys.executable, "-c", code, str(PROJECT_ROOT / "vision")],
                cwd=directory, capture_output=True, text=True, check=True,
            )
        actual = list(map(Path, json.loads(result.stdout)))
        self.assertEqual(actual, [PROJECT_ROOT, PROJECT_ROOT / "vision/models", PROJECT_ROOT / "runtime/uploads"])

    def test_cli_help_needs_no_camera_dependencies(self):
        result = subprocess.run(
            [sys.executable, "-m", "vision", "--help"],
            cwd=PROJECT_ROOT, capture_output=True, text=True, check=True,
        )
        for command in ["monitor", "register", "verify", "recognize", "analyze"]:
            self.assertIn(command, result.stdout)


if __name__ == "__main__":
    unittest.main()
