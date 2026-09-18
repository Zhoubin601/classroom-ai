"""
基于 InsightFace 的人脸注册与特征录入模块
- 支持摄像头实时捕获人脸
- 支持图片文件直接导入注册 (--image 参数)
- 提取 512 维 ArcFace 高维特征向量
- 双向持久化：本地保存 (.npy) + 自动同步至 Java Spring Boot 后端 (MySQL + Redis)
"""

import os
import sys
import json
import argparse
from pathlib import Path

if __package__:
    from .paths import MODELS_DIR, UPLOADS_DIR
else:
    from paths import MODELS_DIR, UPLOADS_DIR

# 强制将标准输出与错误输出设置为 UTF-8，彻底解决 Windows 终端下 Emoji 或中文打印抛出 UnicodeEncodeError
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

# 尝试载入中文字体绘制工具
try:
    from PIL import Image, ImageDraw, ImageFont
    HAS_PIL = True
    FONT_PATH = "C:/Windows/Fonts/msyh.ttc"
except ImportError:
    HAS_PIL = False


def draw_chinese_text(img: np.ndarray, text: str, pos: tuple, color: tuple = (0, 255, 255), size: int = 24) -> np.ndarray:
    """在 OpenCV 图像上绘制清晰的中文字符"""
    if not HAS_PIL or not os.path.exists(FONT_PATH):
        cv2.putText(img, text, pos, cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)
        return img

    img_pil = Image.fromarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
    draw = ImageDraw.Draw(img_pil)
    try:
        font = ImageFont.truetype(FONT_PATH, size)
    except Exception:
        font = ImageFont.load_default()

    # PIL RGB 颜色转换
    rgb_color = (color[2], color[1], color[0])
    draw.text(pos, text, font=font, fill=rgb_color)
    return cv2.cvtColor(np.array(img_pil), cv2.COLOR_RGB2BGR)


def parse_args():
    parser = argparse.ArgumentParser(description="InsightFace 人脸特征注册与云端录入工具")
    parser.add_argument("--id", type=str, default="STU2026001", help="学生学号 (例如: STU2026001)")
    parser.add_argument("--name", type=str, default="张三", help="学生姓名 (例如: 张三)")
    parser.add_argument("--class-name", type=str, default="高一(1)班", help="所属班级")
    parser.add_argument("--gender", type=str, default="MALE", choices=["MALE", "FEMALE", "UNKNOWN"], help="性别")
    parser.add_argument("--camera", type=int, default=0, help="摄像头设备索引 (默认: 0)")
    parser.add_argument("--image", type=str, default=None, help="从本地已有图片文件提取并注册 (可选)")
    parser.add_argument("--backend", type=str, default="http://localhost:8080", help="Java 后端 API 地址")
    args, unknown = parser.parse_known_args()

    # 优先读取前端传入的临时配置文件 register_config.json（彻底避免 Windows 命令行编码和括号特殊字符解析问题）
    cfg_file = Path(__file__).resolve().parent / "register_config.json"
    if cfg_file.exists():
        try:
            with open(cfg_file, "r", encoding="utf-8") as f:
                cfg = json.load(f)
                if "studentId" in cfg and cfg["studentId"]:
                    args.id = str(cfg["studentId"]).strip()
                if "name" in cfg and cfg["name"]:
                    args.name = str(cfg["name"]).strip()
                if "className" in cfg and cfg["className"]:
                    args.class_name = str(cfg["className"]).strip()
                if "gender" in cfg and cfg["gender"]:
                    args.gender = str(cfg["gender"]).strip()
            # 读取后删除临时配置
            try:
                cfg_file.unlink()
            except Exception:
                pass
        except Exception as e:
            print(f"[WARN] 读取 register_config.json 异常: {e}")

    return args


def sync_to_backend(backend_url: str, student_id: str, name: str, class_name: str, gender: str, embedding: np.ndarray, image_rel_path: str = None):
    """
    将 512 维人脸特征与学生档案上报给 Java Spring Boot 后端，存入 MySQL 并刷新 Redis 缓存
    """
    url = f"{backend_url.rstrip('/')}/api/face/register"
    payload = {
        "studentId": student_id,
        "name": name,
        "className": class_name,
        "gender": gender,
        "featureVector": embedding.astype(float).tolist(),
        "imagePath": image_rel_path
    }

    try:
        import urllib.request
        import urllib.error
        req = urllib.request.Request(url, method="POST")
        req.add_header("Content-Type", "application/json")
        data = json.dumps(payload).encode("utf-8")
        with urllib.request.urlopen(req, data=data, timeout=5) as resp:
            res_json = json.loads(resp.read().decode("utf-8"))
            if res_json.get("code") == 200:
                print(f"[OK] 成功同步至 Java 后端 (MySQL + Redis)! 状态: {res_json.get('message')}")
                return True
            else:
                print(f"[WARN] 后端返回错误: {res_json}")
                return False
    except Exception as e:
        print(f"[NOTICE] 未能连接到 Java 后端 ({url}): {e}")
        print("[NOTICE] 本次特征已安全保存在本地 models/ 目录，待后端启动后可自动或手动同步。")
        return False


def save_local(embedding: np.ndarray, student_id: str):
    """保存特征到本地 models 目录"""
    base_dir = MODELS_DIR
    base_dir.mkdir(parents=True, exist_ok=True)

    # 1. 默认更新通用人脸库 face_db.npy
    default_path = base_dir / "face_db.npy"
    np.save(str(default_path), embedding)

    # 2. 同时按学号单独持久化一份
    student_path = base_dir / f"face_db_{student_id}.npy"
    np.save(str(student_path), embedding)

    print(f"[OK] 本地特征持久化成功: {default_path} & {student_path}")


def save_snapshot(frame: np.ndarray, bbox: np.ndarray, student_id: str):
    """保存人脸快照裁剪图"""
    upload_dir = UPLOADS_DIR / "faces"
    upload_dir.mkdir(parents=True, exist_ok=True)

    filename = f"{student_id}_snapshot.jpg"
    dest_path = upload_dir / filename

    h, w = frame.shape[:2]
    x1, y1, x2, y2 = bbox
    # 适当留白
    pad = int(0.2 * max(x2 - x1, y2 - y1))
    crop_x1 = max(0, x1 - pad)
    crop_y1 = max(0, y1 - pad)
    crop_x2 = min(w, x2 + pad)
    crop_y2 = min(h, y2 + pad)

    crop = frame[crop_y1:crop_y2, crop_x1:crop_x2]
    cv2.imwrite(str(dest_path), crop)
    print(f"[OK] 人脸快照图片已保存: {dest_path}")
    return f"/uploads/faces/{filename}"


def register_from_image(app, img_path: str, args):
    """从本地单张图片注册"""
    print(f"\n正在读取图片: {img_path}")
    if not os.path.exists(img_path):
        print(f"[ERROR] 文件不存在: {img_path}")
        return False

    frame = cv2.imread(img_path)
    if frame is None:
        print("[ERROR] 无法解析图片")
        return False

    faces = app.get(frame)
    if len(faces) == 0:
        print("[ERROR] 未在图片中检测到有效人脸！")
        return False

    face = faces[0]
    embedding = face.embedding
    box = face.bbox.astype(int)

    img_rel_path = save_snapshot(frame, box, args.id)
    save_local(embedding, args.id)
    synced = sync_to_backend(args.backend, args.id, args.name, args.class_name, args.gender, embedding, img_rel_path)
    if not synced:
        return False
    print(f"\n[SUCCESS] 学生 [{args.name} ({args.id})] 人脸注册完成！特征维度: {len(embedding)}")


    return synced


def register_from_camera(app, args):
    """从摄像头实时捕获并按 'S' 键注册"""
    cap = cv2.VideoCapture(args.camera, cv2.CAP_DSHOW)
    if not cap.isOpened():
        print(f"[ERROR] 无法打开摄像头设备 (Index: {args.camera})，请检查设备连接或权限。")
        return

    print("=" * 60)
    print(f"启动摄像头注册: 学生={args.name} | 学号={args.id} | 班级={args.class_name}")
    print("操作提示: 请正对摄像头，框选人脸后按键盘 'S' 键确认录入，按 'ESC' 退出。")
    print("=" * 60)

    window_name = "Face Register - Press S to Save"
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(window_name, 960, 720)
    # 强制将窗口置顶并获取焦点，避免被浏览器窗口遮挡
    try:
        cv2.setWindowProperty(window_name, cv2.WND_PROP_TOPMOST, 1)
    except Exception:
        pass

    while True:
        ret, frame = cap.read()
        if not ret:
            print("[WARN] 无法获取摄像头帧")
            break

        faces = app.get(frame)

        # 界面提示信息
        info_text = f"学生: {args.name} ({args.id})  班级: {args.class_name}"
        frame = draw_chinese_text(frame, info_text, (20, 25), (0, 255, 255), 24)
        frame = draw_chinese_text(frame, "【提示】正对镜头，框选人脸后按键盘 S 键保存！ESC退出", (20, 65), (0, 255, 0), 20)

        target_face = None
        for face in faces:
            box = face.bbox.astype(int)
            x1, y1, x2, y2 = box

            # 绘制人脸边界框
            cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
            cv2.putText(frame, "Target Face", (x1, y1 - 10),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
            target_face = face

        cv2.imshow(window_name, frame)
        key = cv2.waitKey(1) & 0xFF

        # 按下 's' 或 'S' 保存
        if key == ord('s') or key == ord('S'):
            if target_face is not None:
                embedding = target_face.embedding
                box = target_face.bbox.astype(int)

                print("\n正在处理人脸特征与持久化...")
                img_rel_path = save_snapshot(frame, box, args.id)
                save_local(embedding, args.id)
                sync_to_backend(args.backend, args.id, args.name, args.class_name, args.gender, embedding, img_rel_path)

                print(f"\n[SUCCESS] 学生 [{args.name} ({args.id})] 人脸录入成功！")
                break
            else:
                print("[WARN] 当前画面中未检测到人脸，请正对摄像头重试。")

        # ESC 键退出
        if key == 27:
            print("\n已取消注册。")
            break

    cap.release()
    cv2.destroyAllWindows()


def main():
    args = parse_args()

    print("\n正在初始化 InsightFace 模型 (buffalo_l)...")
    app = insightface.app.FaceAnalysis(
        name="buffalo_l",
        providers=["CUDAExecutionProvider", "CPUExecutionProvider"]
    )
    app.prepare(ctx_id=0, det_size=(640, 640))
    print("模型加载就绪！")

    if args.image:
        return 0 if register_from_image(app, args.image, args) else 1
    else:
        register_from_camera(app, args)


if __name__ == "__main__":
    main()
