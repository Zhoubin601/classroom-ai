# -*- coding: utf-8 -*-
"""
智能课堂实时视觉感知与督导推断模块 (Classroom AI Realtime Monitor)
- 接入摄像头实时视频流 (OpenCV + DShow)
- 基于 InsightFace (buffalo_l, CUDA 12 GPU 加速):
  * RetinaFace 实时人脸检测
  * ArcFace 提取 512 维特征向量，实时 1:N 余弦比对匹配学生身份
  * 3D Landmark 姿态估计：直接解算高精度 Pitch (俯仰角)、Yaw (偏航角)、Roll (翻滚角)
  * 精准判定学生状态：抬头听课 (UP) / 低头走神 (DOWN)
- 统计在场人数、识别建档人数、实时抬头率与低头走神预警数
- 异步非阻塞上报 Java Spring Boot 后端 (MySQL + Redis)
- 驱动大屏 ECharts 时序波形与学生实时状态矩阵无缝流动
"""

import os
import sys
import time
import json
import argparse
import threading
from pathlib import Path

if __package__:
    from .paths import PROJECT_ROOT
else:
    from paths import PROJECT_ROOT
from typing import Dict, List, Tuple
from http.server import BaseHTTPRequestHandler, HTTPServer
import socketserver

# 1. 强制将控制台输出设置为 UTF-8，并设置控制台标题，彻底解决 Windows 终端编码与窗口定位
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
        import ctypes
        ctypes.windll.kernel32.SetConsoleTitleW("Classroom AI Robot Vision Monitor")
    except Exception:
        pass

# 2. 动态加载 Windows 下 CUDA 12 / cuDNN 依赖动态库
if sys.platform == "win32":
    try:
        from importlib.metadata import distribution
        _dll_dirs = sorted({str(distribution(pkg).locate_file(f).parent)
                            for pkg in ("nvidia-cudnn-cu12", "nvidia-cublas-cu12",
                                        "nvidia-cuda-runtime-cu12", "nvidia-cuda-nvrtc-cu12",
                                        "nvidia-cufft-cu12", "nvidia-curand-cu12",
                                        "nvidia-nvjitlink-cu12")
                            for f in distribution(pkg).files or []
                            if str(f).lower().endswith(".dll")})
        for d in _dll_dirs:
            os.add_dll_directory(d)
        os.environ["PATH"] = os.pathsep.join(_dll_dirs + [os.environ.get("PATH", "")])
    except Exception:
        pass

import cv2
import numpy as np
import urllib.request
import urllib.error
import insightface
import warnings
warnings.filterwarnings("ignore", category=FutureWarning)
warnings.filterwarnings("ignore", category=UserWarning)

# 3. 中文字体支持 (PIL)
try:
    from PIL import Image, ImageDraw, ImageFont
    HAS_PIL = True
    FONT_PATH = "C:/Windows/Fonts/msyh.ttc"
except ImportError:
    HAS_PIL = False
    FONT_PATH = ""


def draw_chinese_text(img: np.ndarray, text: str, pos: tuple, color: tuple = (0, 255, 255), size: int = 18) -> np.ndarray:
    """在 OpenCV 图像上高质量绘制中文字符"""
    if not HAS_PIL or not os.path.exists(FONT_PATH):
        cv2.putText(img, text, pos, cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)
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


latest_jpeg_frame = None
frame_lock = threading.Lock()
monitor_instance = None


class ThreadedHTTPServer(socketserver.ThreadingMixIn, HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


class MJPEGHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        global latest_jpeg_frame, monitor_instance
        if self.path.startswith('/video_feed'):
            self.send_response(200)
            self.send_header('Content-Type', 'multipart/x-mixed-replace; boundary=frame')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
            self.send_header('Pragma', 'no-cache')
            self.end_headers()
            while monitor_instance and monitor_instance.running:
                with frame_lock:
                    data = latest_jpeg_frame
                if data is not None:
                    try:
                        self.wfile.write(b'--frame\r\n')
                        self.send_header('Content-Type', 'image/jpeg')
                        self.send_header('Content-Length', str(len(data)))
                        self.end_headers()
                        self.wfile.write(data)
                        self.wfile.write(b'\r\n')
                    except Exception:
                        break
                time.sleep(0.04)
        elif self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            ready = bool(monitor_instance and monitor_instance.running and latest_jpeg_frame is not None)
            self.wfile.write(json.dumps({"status": "ok" if ready else "starting", "running": ready}).encode("utf-8"))
        elif self.path == '/stop':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(b'{"status":"stopping"}')
            if monitor_instance:
                monitor_instance.running = False
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass


class ClassroomMonitor:
    def __init__(self, backend_url: str = "http://localhost:8080", camera_index: int = 0, no_window: bool = False, port: int = 8088):
        global monitor_instance
        monitor_instance = self
        self.backend_url = backend_url.rstrip("/")
        self.camera_index = camera_index
        self.no_window = no_window
        self.port = port
        self.running = True

        self.project_root = PROJECT_ROOT
        self.student_db: Dict[str, dict] = {}  # { studentId: { name, className, vector: np.ndarray } }

        # 上报节流与线程管理 (每 1.2 秒向后端推送一次聚合指标)
        self.last_report_time = 0.0
        self.report_interval = 1.2
        self.is_reporting = False

        # 启动内置 HTTP MJPEG 实时流服务
        self._start_http_server()

        # 初始化模型与底库
        self._init_insightface()
        self._load_face_database(verbose=True)
        self._start_db_sync_thread()

    def _start_http_server(self):
        """启动内置轻量 HTTP 视频流与健康检测服务"""
        def _serve():
            try:
                server = ThreadedHTTPServer(("127.0.0.1", self.port), MJPEGHandler)
                print(f">> [HTTP] 网页实时视频流服务已就绪: http://localhost:{self.port}/video_feed")
                server.serve_forever()
            except Exception as e:
                print(f">> [HTTP NOTICE] 网页视频流启动异常: {e}")
        t = threading.Thread(target=_serve, daemon=True)
        t.start()

    def _init_insightface(self):
        """初始化 InsightFace 检测、识别与 3D 姿态模型 (buffalo_l)"""
        print("\n" + "=" * 65)
        print(">> [1/2] 正在加载 InsightFace 视觉推断引擎 (buffalo_l, CUDA GPU 加速)...")
        self.app = insightface.app.FaceAnalysis(
            name="buffalo_l",
            providers=["CUDAExecutionProvider", "CPUExecutionProvider"]
        )
        self.app.prepare(ctx_id=0, det_size=(640, 640))
        print("   InsightFace 视觉检测识别与 3D 姿态估计模型全部就绪！")

    def _start_db_sync_thread(self):
        """后台定时与云端数据库同步底库 (每 3 秒)，实现前端删除/新增学生无需重启监控即时生效"""
        def _loop():
            while self.running:
                time.sleep(3)
                try:
                    self._load_face_database(verbose=False)
                except Exception:
                    pass
        t = threading.Thread(target=_loop, daemon=True)
        t.start()

    def _load_face_database(self, verbose: bool = False):
        """从 Java Spring Boot 后端 (MySQL + Redis) 全量拉取已录入学生底库 (以云端数据库为唯一数据源)"""
        if verbose:
            print(">> [2/2] 正在从云端 (MySQL + Redis) 同步学生人脸 512 维特征底库...")

        url = f"{self.backend_url}/api/face/all"
        try:
            req = urllib.request.Request(url, method="GET")
            req.add_header("Accept", "application/json")
            with urllib.request.urlopen(req, timeout=3) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                if data.get("code") == 200 and isinstance(data.get("data"), list):
                    new_db = {}
                    for item in data["data"]:
                        stu_id = item.get("studentId")
                        vec = item.get("featureVector")
                        if stu_id and vec and len(vec) == 512:
                            new_db[stu_id] = {
                                "name": item.get("name", "Unknown"),
                                "className": item.get("className", "高一(1)班"),
                                "vector": np.array(vec, dtype=np.float32)
                            }
                    self.student_db = new_db
                    if verbose:
                        print(f"   [OK] 成功从云端同步档案，当前建档人数: {len(self.student_db)} 位！")
        except Exception as e:
            if verbose:
                print(f"   [NOTICE] 云端底库拉取异常: {e}")

    def match_student(self, embedding: np.ndarray, threshold: float = 0.40) -> Tuple[str, str, float]:
        """与底库特征进行 1:N 余弦相似度比对，判定学生身份"""
        if not self.student_db:
            return "Unknown", "访客", 0.0

        best_id = "Unknown"
        best_name = "访客"
        best_sim = -1.0

        norm_embed = np.linalg.norm(embedding)
        if norm_embed <= 0:
            return "Unknown", "访客", 0.0

        for stu_id, info in self.student_db.items():
            db_vec = info["vector"]
            norm_db = np.linalg.norm(db_vec)
            if norm_db <= 0:
                continue

            sim = float(np.dot(embedding, db_vec) / (norm_embed * norm_db))
            if sim > best_sim:
                best_sim = sim
                if sim >= threshold:
                    best_id = stu_id
                    best_name = info["name"]

        return best_id, best_name, max(0.0, best_sim)

    def parse_head_pose(self, face) -> Tuple[float, float, float, str]:
        """
        从 InsightFace 3D 姿态模型解析 Pitch (俯仰角)、Yaw (偏航角)、Roll (翻滚角)
        Pitch 判定标准：
          - Pitch >= -10.0°：判定为【抬头听课 (UP)】
          - Pitch < -10.0°：判定为【低头看书/玩手机/走神 (DOWN)】
        """
        if "pose" in face and face.pose is not None and len(face.pose) >= 3:
            pitch, yaw, roll = float(face.pose[0]), float(face.pose[1]), float(face.pose[2])
        else:
            # 备用：若 pose 不存在则根据 5 点 kps 眼睛与鼻子相对垂直位移推断
            pitch = 0.0
            yaw = 0.0
            roll = 0.0
            if "kps" in face and face.kps is not None:
                kps = face.kps
                eye_center_y = (kps[0][1] + kps[1][1]) / 2.0
                nose_y = kps[2][1]
                mouth_center_y = (kps[3][1] + kps[4][1]) / 2.0
                upper_ratio = (nose_y - eye_center_y) / max(1.0, (mouth_center_y - eye_center_y))
                # 正常比率约 0.45~0.55，低头时鼻子相对更靠近嘴角
                pitch = (upper_ratio - 0.50) * -100.0

        state = "DOWN" if pitch < -10.0 else "UP"
        return pitch, yaw, roll, state

    def async_send_stream_report(self, payload: dict):
        """异步非阻塞向后端上报实时推断聚合数据，彻底不拖慢 OpenCV 视频流"""
        def _post():
            try:
                url = f"{self.backend_url}/api/visual/report/stream"
                data = json.dumps(payload).encode("utf-8")
                req = urllib.request.Request(url, data=data, method="POST")
                req.add_header("Content-Type", "application/json")
                with urllib.request.urlopen(req, timeout=3) as resp:
                    pass
            except Exception:
                pass
            finally:
                self.is_reporting = False

        if not self.is_reporting:
            self.is_reporting = True
            threading.Thread(target=_post, daemon=True).start()

    def run(self):
        """主推断循环"""
        print(f"正在启动摄像头设备 (Index: {self.camera_index})...")
        cap = cv2.VideoCapture(self.camera_index, cv2.CAP_DSHOW)
        if not cap.isOpened():
            print(f"[ERROR] 无法连接摄像头设备 (Index: {self.camera_index})！请检查硬件设备或权限。")
            return

        cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)

        window_name = "Classroom AI Robot Vision Monitor"
        if not self.no_window:
            cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
            cv2.resizeWindow(window_name, 1100, 680)
            try:
                cv2.setWindowProperty(window_name, cv2.WND_PROP_TOPMOST, 1)
            except Exception:
                pass

        print("=" * 65)
        print("🎯 课堂智能视觉督导推断流已正式开始运行！")
        print(f"   - 实时特征比对：已挂载 {len(self.student_db)} 位学生底库")
        print("   - 实时姿态估计：正在解算 Pitch 俯仰角与 UP/DOWN 状态")
        print("   - 自动数据推流：每 1.2 秒自动推送大屏并持久化 Redis")
        print("   - 退出监控：请在画面窗口中按键盘 'ESC' 或 'Q' 键")
        print("=" * 65 + "\n")

        fps_counter = 0
        fps_start_time = time.time()
        fps = 0.0

        consecutive_cam_fails = 0
        while self.running:
            ret, frame = cap.read()
            if not ret:
                consecutive_cam_fails += 1
                if consecutive_cam_fails > 15:
                    print("[ERROR] 连续多次无法读取摄像头画面，退出监控循环")
                    break
                time.sleep(0.05)
                continue
            consecutive_cam_fails = 0

            h, w = frame.shape[:2]
            fps_counter += 1
            if time.time() - fps_start_time >= 1.0:
                fps = fps_counter / (time.time() - fps_start_time)
                fps_counter = 0
                fps_start_time = time.time()

            # 1. 单次 GPU 推断完成：人脸检测 + 512维特征 + 3D姿态角解算
            faces = self.app.get(frame)

            detected_count = len(faces)
            lookup_count = 0
            lookdown_count = 0
            present_student_ids: List[str] = []
            student_poses: Dict[str, str] = {}

            for face in faces:
                bbox = face.bbox.astype(int)
                x1, y1, x2, y2 = bbox
                embedding = face.embedding

                # 1:N 身份比对
                student_id, student_name, sim = self.match_student(embedding)

                # 姿态解算
                pitch, yaw, roll, pose_state = self.parse_head_pose(face)

                if pose_state == "UP":
                    lookup_count += 1
                else:
                    lookdown_count += 1

                # 记录已知学生的实时考勤与姿态
                if student_id != "Unknown":
                    present_student_ids.append(student_id)
                    student_poses[student_id] = pose_state

                # 绘制人脸框与科技 HUD 标注
                # 绿色表示抬头听课，红色表示低头走神
                box_color = (0, 255, 0) if pose_state == "UP" else (0, 0, 255)
                cv2.rectangle(frame, (x1, y1), (x2, y2), box_color, 2)

                # 四角拐角高亮
                corner_len = int(min(x2 - x1, y2 - y1) * 0.2)
                cv2.line(frame, (x1, y1), (x1 + corner_len, y1), box_color, 4)
                cv2.line(frame, (x1, y1), (x1, y1 + corner_len), box_color, 4)
                cv2.line(frame, (x2, y1), (x2 - corner_len, y1), box_color, 4)
                cv2.line(frame, (x2, y1), (x2, y1 + corner_len), box_color, 4)
                cv2.line(frame, (x1, y2), (x1 + corner_len, y2), box_color, 4)
                cv2.line(frame, (x1, y2), (x1, y2 - corner_len), box_color, 4)
                cv2.line(frame, (x2, y2), (x2 - corner_len, y2), box_color, 4)
                cv2.line(frame, (x2, y2), (x2, y2 - corner_len), box_color, 4)

                # 文字标签
                state_text = "【抬头听课】" if pose_state == "UP" else "【低头走神】"
                if student_id != "Unknown":
                    display_text = f"{student_name} ({student_id}) | {state_text} Pitch:{pitch:.1f}°"
                else:
                    display_text = f"访客学生 (未建档) | {state_text} Pitch:{pitch:.1f}°"

                frame = draw_chinese_text(frame, display_text, (x1, max(12, y1 - 26)), box_color, 18)

            # 计算实时平均抬头率
            lookup_rate = (lookup_count / detected_count) if detected_count > 0 else 0.0

            # 2. 定时向 Spring Boot 后端上报数据 (驱动大屏实时刷新)
            current_time = time.time()
            if current_time - self.last_report_time >= self.report_interval:
                self.last_report_time = current_time
                payload = {
                    "sessionId": "CLASS_LIVE_MONITOR",
                    "courseName": "具身智能课堂分析与实训",
                    "className": "高一(1)班",
                    "detectedPersonCount": detected_count,
                    "lookupCount": lookup_count,
                    "lookdownCount": lookdown_count,
                    "lookupRate": float(round(lookup_rate, 3)),
                    "presentStudentIds": present_student_ids,
                    "studentPoses": student_poses
                }
                self.async_send_stream_report(payload)

            # 3. 绘制画面顶部半透明 HUD 综合看板
            hud_bg = frame[:55, :].copy()
            cv2.rectangle(frame, (0, 0), (w, 55), (15, 23, 42), -1)
            frame[:55, :] = cv2.addWeighted(hud_bg, 0.2, frame[:55, :], 0.8, 0)

            hud_text = (
                f"在座人数: {detected_count} 人  |  "
                f"识别建档: {len(present_student_ids)} 人  |  "
                f"实时抬头率: {lookup_rate * 100:.1f}%  |  "
                f"低头预警: {lookdown_count} 人  |  "
                f"推断帧率: {fps:.1f} FPS"
            )
            frame = draw_chinese_text(frame, hud_text, (20, 16), (0, 245, 255), 19)

            # 3.1 编码 JPEG 供给 Web 端实时流 (<img src="/api/visual/video-feed">)
            try:
                ret_enc, jpeg_buf = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
                if ret_enc:
                    global latest_jpeg_frame
                    with frame_lock:
                        latest_jpeg_frame = jpeg_buf.tobytes()
            except Exception:
                pass

            # 4. 渲染画面
            if not self.no_window:
                cv2.imshow(window_name, frame)
                key = cv2.waitKey(1) & 0xFF
                if key == 27 or key == ord('q') or key == ord('Q'):
                    print("\n正在停止智能视觉督导推断...")
                    self.running = False
                    break

        self.running = False
        cap.release()
        cv2.destroyAllWindows()
        print("视觉督导程序已退出，摄像头与硬件设备已完全释放。")


def main():
    parser = argparse.ArgumentParser(description="智能课堂机器人实时视觉感知与督导推断系统")
    parser.add_argument("--camera", type=int, default=0, help="摄像头设备索引 (默认 0)")
    parser.add_argument("--backend", type=str, default="http://localhost:8080", help="Java 后端 API 地址")
    parser.add_argument("--port", type=int, default=8088, help="Web MJPEG 视频流端口 (默认 8088)")
    parser.add_argument("--no-window", action="store_true", help="无头模式 (不显示 OpenCV 桌面视窗)")
    args = parser.parse_args()

    monitor = ClassroomMonitor(
        backend_url=args.backend,
        camera_index=args.camera,
        no_window=args.no_window,
        port=args.port
    )
    monitor.run()


if __name__ == "__main__":
    main()
