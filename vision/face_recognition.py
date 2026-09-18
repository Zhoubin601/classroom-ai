import sys
from pathlib import Path

if __package__:
    from .paths import MODELS_DIR
else:
    from paths import MODELS_DIR

import cv2
import insightface
import numpy as np
import onnxruntime as ort


# Load the CUDA/cuDNN libraries installed in this Python environment.
print("Python:", sys.executable)
print("ONNX Runtime:", ort.__version__)
if not hasattr(ort, "preload_dlls"):
    raise RuntimeError("Use .venv1 with onnxruntime-gpu[cuda,cudnn]==1.21.1")
# cuDNN also loads sub-libraries during inference via the Windows DLL search path.
_dll_handles = []
if sys.platform == "win32":
    import os
    from importlib.metadata import distribution
    _dll_dirs = sorted({str(distribution(package).locate_file(file).parent)
                        for package in ("nvidia-cudnn-cu12", "nvidia-cublas-cu12",
                                        "nvidia-cuda-runtime-cu12", "nvidia-cuda-nvrtc-cu12",
                                        "nvidia-cufft-cu12", "nvidia-curand-cu12",
                                        "nvidia-nvjitlink-cu12")
                        for file in distribution(package).files or []
                        if str(file).lower().endswith(".dll")})
    for directory in _dll_dirs:
        _dll_handles.append(os.add_dll_directory(directory))
    os.environ["PATH"] = os.pathsep.join(_dll_dirs + [os.environ.get("PATH", "")])
ort.preload_dlls(directory="")
if "CUDAExecutionProvider" not in ort.get_available_providers():
    raise RuntimeError("CUDA provider unavailable. Check the selected Python environment.")

print("ONNX Runtime providers:")
print(ort.get_available_providers())


# ==========================
# InsightFace
# ==========================

app = insightface.app.FaceAnalysis(
    name="buffalo_l",
    providers=[
        "CUDAExecutionProvider",
        "CPUExecutionProvider"
    ]
)


app.prepare(
    ctx_id=0,
    det_size=(640, 640)
)


# 查看实际运行设备

print("\nModel providers:")

for name, model in app.models.items():

    print("----------------")
    print(name)

    try:
        print(
            model.session.get_providers()
        )

    except:
        print("no provider info")


# Fail clearly if any model silently falls back to CPU.
for model_name, model in app.models.items():
    if "CUDAExecutionProvider" not in model.session.get_providers():
        raise RuntimeError(f"{model_name}: CUDA initialization failed; see errors above.")

# Validate all five models without opening the camera or reading face data.
if "--check-gpu" in sys.argv:
    for model_name, model in app.models.items():
        session = model.session
        session.disable_fallback()
        inp = session.get_inputs()[0]
        size = 640 if model_name == "detection" else model.input_size[0]
        shape = [dim if isinstance(dim, int) and dim > 0 else
                 (1 if axis == 0 else size) for axis, dim in enumerate(inp.shape)]
        result = session.run(None, {inp.name: np.zeros(shape, dtype=np.float32)})
        if not all(np.isfinite(value).all() for value in result):
            raise RuntimeError(f"{model_name}: non-finite output")
        if "CUDAExecutionProvider" not in session.get_providers():
            raise RuntimeError(f"{model_name}: CPU fallback during inference")
        print(f"GPU inference OK: {model_name}")
    print("GPU check passed.")
    sys.exit(0)


# ==========================
# 加载人脸数据库
# ==========================

db = np.load(
    MODELS_DIR / "face_db.npy"
)


# ==========================
# 摄像头
# ==========================

cap = cv2.VideoCapture(
    0,
    cv2.CAP_DSHOW
)


cap.set(
    cv2.CAP_PROP_FRAME_WIDTH,
    640
)

cap.set(
    cv2.CAP_PROP_FRAME_HEIGHT,
    480
)



while True:


    ret, frame = cap.read()


    if not ret:
        break



    # 人脸检测+特征提取

    faces = app.get(frame)



    for face in faces:


        embedding = face.embedding



        # 余弦相似度

        similarity = (
            np.dot(
                embedding,
                db
            )
            /
            (
                np.linalg.norm(embedding)
                *
                np.linalg.norm(db)
            )
        )


        name = "Unknown"


        if similarity > 0.4:

            name = "You"



        box = face.bbox.astype(int)


        x1,y1,x2,y2 = box



        cv2.rectangle(
            frame,
            (x1,y1),
            (x2,y2),
            (0,255,0),
            2
        )


        cv2.putText(
            frame,
            f"{name}:{similarity:.2f}",
            (x1,y1-10),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.8,
            (0,255,0),
            2
        )



    cv2.imshow(
        "Recognition",
        frame
    )



    if cv2.waitKey(1)==27:
        break



cap.release()

cv2.destroyAllWindows()
