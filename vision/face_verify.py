"""
基于 InsightFace 的 1:N 现场人脸认证比对模块
- 模式 1: 接收前端摄像头单张快照 (--image)，提取 512 维特征向量并在 Java 后端执行 1:N 检索
- 模式 2: 调起独立桌面 OpenCV 窗口实时视频流刷脸识别比对
"""

import os
import sys
import json
import argparse
from pathlib import Path

if __package__:
    from .paths import UPLOADS_DIR
else:
    from paths import UPLOADS_DIR

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import cv2
import numpy as np
import insightface

# 尝试载入 Windows 下 CUDA/cuDNN DLL 支持
if sys.platform == "win32":
    try:
        from importlib.metadata import distribution
        _dll_dirs = sorted({str(distribution(package).locate_file(file).parent)
                            for package in ("nvidia-cudnn-cu12", "nvidia-cublas-cu12",
                                            "nvidia-cuda-runtime-cu12", "nvidia-cuda-nvrtc-cu12",
                                            "nvidia-cufft-cu12", "nvidia-curand-cu12",
                                            "nvidia-nvjitlink-cu12")
                            for file in distribution(package).files or []
                            if str(file).lower().endswith(".dll")})
        for directory in _dll_dirs:
            os.add_dll_directory(directory)
        os.environ["PATH"] = os.pathsep.join(_dll_dirs + [os.environ.get("PATH", "")])
    except Exception:
        pass

try:
    from PIL import Image, ImageDraw, ImageFont
    HAS_PIL = True
    FONT_PATH = "C:/Windows/Fonts/msyh.ttc"
except ImportError:
    HAS_PIL = False


def draw_chinese_text(img: np.ndarray, text: str, pos: tuple, color: tuple = (0, 255, 255), size: int = 22) -> np.ndarray:
    if not HAS_PIL or not os.path.exists(FONT_PATH):
        cv2.putText(img, text, pos, cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)
        return img

    img_pil = Image.fromarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
    draw = ImageDraw.Draw(img_pil)
    try:
        font = ImageFont.truetype(FONT_PATH, size)
    except Exception:
        font = ImageFont.load_default()

    rgb_color = (color[2], color[1], color[0])
    draw.text(pos, text, font=font, fill=rgb_color)
    return cv2.cvtColor(np.array(img_pil), cv2.COLOR_RGB2BGR)


def parse_args():
    parser = argparse.ArgumentParser(description="InsightFace 1:N 人脸现场认证比对工具")
    parser.add_argument("--image", type=str, default=None, help="待认证快照图片路径")
    parser.add_argument("--threshold", type=float, default=0.45, help="余弦相似度判定阈值")
    parser.add_argument("--camera", type=int, default=0, help="摄像头设备索引 (默认: 0)")
    parser.add_argument("--backend", type=str, default="http://localhost:8080", help="后端 API 地址")
    return parser.parse_args()


def save_verify_snapshot(frame: np.ndarray, bbox: np.ndarray) -> str:
    upload_dir = UPLOADS_DIR / "temp"
    upload_dir.mkdir(parents=True, exist_ok=True)

    import uuid
    filename = f"verify_{uuid.uuid4().hex[:12]}.jpg"
    dest_path = upload_dir / filename

    h, w = frame.shape[:2]
    x1, y1, x2, y2 = bbox
    pad = int(0.2 * max(x2 - x1, y2 - y1))
    crop_x1 = max(0, x1 - pad)
    crop_y1 = max(0, y1 - pad)
    crop_x2 = min(w, x2 + pad)
    crop_y2 = min(h, y2 + pad)

    crop = frame[crop_y1:crop_y2, crop_x1:crop_x2]
    cv2.imwrite(str(dest_path), crop)
    return f"/uploads/temp/{filename}"


def search_face_backend(backend_url: str, embedding: np.ndarray, threshold: float) -> dict:
    url = f"{backend_url.rstrip('/')}/api/face/search"
    payload = {
        "featureVector": embedding.astype(float).tolist(),
        "threshold": threshold,
        "topK": 1
    }
    try:
        import urllib.request
        req = urllib.request.Request(url, method="POST")
        req.add_header("Content-Type", "application/json")
        data = json.dumps(payload).encode("utf-8")
        with urllib.request.urlopen(req, data=data, timeout=8) as resp:
            res_json = json.loads(resp.read().decode("utf-8"))
            if res_json.get("code") == 200:
                return res_json.get("data", {})
    except Exception as e:
        sys.stderr.write(f"连接后端检索接口异常: {e}\n")
    return None


def verify_image(app, img_path: str, threshold: float, backend_url: str):
    if not os.path.exists(img_path):
        result = {"code": 400, "success": False, "message": f"文件不存在: {img_path}"}
        print(json.dumps(result, ensure_ascii=False))
        return False

    frame = cv2.imread(img_path)
    if frame is None:
        result = {"code": 400, "success": False, "message": "无法解析图片文件"}
        print(json.dumps(result, ensure_ascii=False))
        return False

    faces = app.get(frame)
    if len(faces) == 0:
        result = {
            "code": 400,
            "success": False,
            "message": "未在画面中检测到有效人脸，请正对摄像头保持光线充足！"
        }
        print(json.dumps(result, ensure_ascii=False))
        return False

    faces = sorted(faces, key=lambda f: (f.bbox[2] - f.bbox[0]) * (f.bbox[3] - f.bbox[1]), reverse=True)
    main_face = faces[0]
    embedding = main_face.embedding
    box = main_face.bbox.astype(int)

    snapshot_url = save_verify_snapshot(frame, box)
    match_data = search_face_backend(backend_url, embedding, threshold)

    if match_data is None:
        result = {
            "code": 500,
            "success": False,
            "message": "连接后端人脸检索服务失败，请确认后端 8080 端口正常运行"
        }
        print(json.dumps(result, ensure_ascii=False))
        return False

    result = {
        "code": 200,
        "success": True,
        "matched": match_data.get("matched", False),
        "studentId": match_data.get("studentId", "Unknown"),
        "name": match_data.get("name", "未知人员"),
        "className": match_data.get("className", "-"),
        "similarity": match_data.get("similarity", 0.0),
        "threshold": threshold,
        "avatarUrl": match_data.get("avatarUrl", ""),
        "snapshotUrl": snapshot_url,
        "detectedFaces": len(faces)
    }
    print(json.dumps(result, ensure_ascii=False))
    return True


def verify_camera_stream(app, camera_index: int, threshold: float, backend_url: str):
    import urllib.request
    registered_faces = []
    try:
        url = f"{backend_url.rstrip('/')}/api/face/all"
        with urllib.request.urlopen(url, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            if data.get("code") == 200:
                registered_faces = data.get("data", [])
    except Exception as e:
        print(f"[WARN] 无法从后端预拉取人脸库: {e}")

    cap = cv2.VideoCapture(camera_index, cv2.CAP_DSHOW)
    if not cap.isOpened():
        print(f"[ERROR] 无法打开摄像头设备 (Index: {camera_index})")
        return

    window_name = "Face Verify - Real-time 1:N Match"
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(window_name, 960, 720)
    try:
        cv2.setWindowProperty(window_name, cv2.WND_PROP_TOPMOST, 1)
    except Exception:
        pass

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        faces = app.get(frame)
        frame = draw_chinese_text(frame, f"【1:N 现场人脸认证】底库学生: {len(registered_faces)} 人 | 阈值: {threshold}", (20, 25), (0, 255, 255), 22)
        frame = draw_chinese_text(frame, "操作提示: 按 ESC 键关闭退出窗口", (20, 60), (200, 200, 200), 18)

        for face in faces:
            embedding = face.embedding
            box = face.bbox.astype(int)
            x1, y1, x2, y2 = box

            best_sim = -1.0
            best_stu = None

            norm_a = np.linalg.norm(embedding)
            if norm_a > 0:
                for candidate in registered_faces:
                    vec = candidate.get("featureVector")
                    if vec and len(vec) == 512:
                        vec_np = np.array(vec, dtype=np.float32)
                        norm_b = np.linalg.norm(vec_np)
                        if norm_b > 0:
                            sim = float(np.dot(embedding, vec_np) / (norm_a * norm_b))
                            if sim > best_sim:
                                best_sim = sim
                                best_stu = candidate

            is_matched = best_sim >= threshold and best_stu is not None
            color = (0, 255, 0) if is_matched else (0, 0, 255)
            cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)

            if is_matched:
                label = f"{best_stu.get('name', 'Student')} ({best_sim:.2f})"
            else:
                label = f"Unknown ({best_sim:.2f})"

            frame = draw_chinese_text(frame, label, (x1, max(15, y1 - 25)), color, 20)

        cv2.imshow(window_name, frame)
        if cv2.waitKey(1) == 27:
            break

    cap.release()
    cv2.destroyAllWindows()


def main():
    args = parse_args()
    app = insightface.app.FaceAnalysis(
        name="buffalo_l",
        providers=["CUDAExecutionProvider", "CPUExecutionProvider"]
    )
    app.prepare(ctx_id=0, det_size=(640, 640))

    if args.image:
        verify_image(app, args.image, args.threshold, args.backend)
        sys.exit(0)
    else:
        verify_camera_stream(app, args.camera, args.threshold, args.backend)


if __name__ == "__main__":
    main()
