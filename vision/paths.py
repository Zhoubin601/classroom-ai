"""Resolve shared resources independently of the caller's working directory."""
from pathlib import Path

VISION_ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = VISION_ROOT.parent
MODELS_DIR = VISION_ROOT / "models"
UPLOADS_DIR = PROJECT_ROOT / "runtime" / "uploads"
