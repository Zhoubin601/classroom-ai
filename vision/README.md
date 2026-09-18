# Python 视觉模块

当前在用的五个视觉脚本统一放在本目录。脚本支持直接执行，也支持在项目根目录使用 `python -m vision <command>`。

| 命令 | 实现 | 用途 |
| --- | --- | --- |
| `monitor` | `classroom_monitor.py` | 摄像头监控、状态上报、8088 MJPEG 服务 |
| `register` | `face_register.py` | 摄像头或 `--image` 图片注册并同步后端 |
| `verify` | `face_verify.py` | 摄像头或图片识别，查询后端人脸库 |
| `recognize` | `face_recognition.py` | 本地特征库 GPU 识别实验 |
| `analyze` | `yolo_face_analysis.py` | YOLO + MediaPipe 姿态分析实验 |

## 依赖与数据

- 核心依赖：`requirements.txt`。需要 Python 3.10+；GPU 工具需要兼容的 NVIDIA 驱动与 CUDA 运行环境。
- 可选实验依赖：`requirements-analysis.txt`，包含 MediaPipe、Ultralytics。现有 `.venv1` 未发现这两个包，本次不自动安装或改变现有环境。
- `models/` 保存迁入的 YOLO、MediaPipe 模型和本地 `.npy` 特征；InsightFace 仍使用其原有缓存机制。
- 图片路径统一由 `paths.py` 指向项目根目录 `runtime/uploads/`，不受执行命令时的工作目录影响。
- `.venv1/` 继续保留在项目根目录；前端可用 `CLASSROOM_PYTHON` 指定其他 Python 可执行文件。
- 新增的依赖清单用于描述安装入口，本次未新建环境验证安装；当前环境回归结果见重构记录。

## 独立启动

```powershell
# 从项目根目录
.\.venv1\Scripts\python.exe -m vision monitor --port 8088 --no-window

# 从 vision 目录
..\.venv1\Scripts\python.exe classroom_monitor.py --port 8088
```

## 测试与诊断

`tests/` 中的测试只使用标准库，不打开摄像头、不下载模型、不写入真实人脸库。
`diagnostics/` 保存原来的相机、ONNX Runtime、YOLO、MediaPipe、姿态实验，按需逐个手动运行。
不要对 `diagnostics/` 进行无人值守的测试发现；部分脚本在导入时就会访问设备。

