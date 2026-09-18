import cv2
import mediapipe as mp
import numpy as np
import math

from pathlib import Path
from mediapipe.tasks import python
from mediapipe.tasks.python import vision


# ==========================
# MediaPipe初始化
# ==========================

base_options = python.BaseOptions(
    model_asset_path=str(Path(__file__).resolve().parent.parent / "models" / "face_landmarker.task")
)

options = vision.FaceLandmarkerOptions(
    base_options=base_options,
    num_faces=1
)

detector = vision.FaceLandmarker.create_from_options(
    options
)


# ==========================
# 3D人脸模型点
# ==========================

face_3d_model = np.array([
    [0.0, 0.0, 0.0],        # 鼻尖
    [0.0, -63.6, -12.5],    # 下巴
    [-43.3, 32.7, -26],     # 左眼角
    [43.3, 32.7, -26],      # 右眼角
    [-28.9, -28.9, -24.1],  # 左嘴角
    [28.9, -28.9, -24.1]    # 右嘴角

], dtype=np.float64)


# MediaPipe关键点编号

landmark_ids = [
    1,
    152,
    33,
    263,
    61,
    291
]


# 摄像头

cap = cv2.VideoCapture(
    0,
    cv2.CAP_DSHOW
)


while True:

    ret, frame = cap.read()

    if not ret:
        break


    h, w, _ = frame.shape


    rgb = cv2.cvtColor(
        frame,
        cv2.COLOR_BGR2RGB
    )


    mp_image = mp.Image(
        image_format=mp.ImageFormat.SRGB,
        data=rgb
    )


    result = detector.detect(mp_image)


    if result.face_landmarks:


        face = result.face_landmarks[0]


        face_2d = []


        for idx in landmark_ids:

            lm = face[idx]

            x = int(lm.x * w)
            y = int(lm.y * h)

            face_2d.append([x,y])


        face_2d = np.array(
            face_2d,
            dtype=np.float64
        )


        # 摄像头内参

        focal_length = w

        center = (
            w/2,
            h/2
        )


        camera_matrix = np.array(
            [
                [focal_length,0,center[0]],
                [0,focal_length,center[1]],
                [0,0,1]
            ],
            dtype=np.float64
        )


        dist_coeffs = np.zeros(
            (4,1)
        )


        success, rotation_vec, translation_vec = cv2.solvePnP(
            face_3d_model,
            face_2d,
            camera_matrix,
            dist_coeffs
        )


        if success:

            rotation_matrix, _ = cv2.Rodrigues(
                rotation_vec
            )


            angles, _, _, _, _, _ = cv2.RQDecomp3x3(
                rotation_matrix
            )


            pitch = angles[0] * 360
            yaw = angles[1] * 360
            roll = angles[2] * 360


            cv2.putText(
                frame,
                f"Pitch:{pitch:.2f}",
                (20,40),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0,255,0),
                2
            )


            cv2.putText(
                frame,
                f"Yaw:{yaw:.2f}",
                (20,70),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0,255,0),
                2
            )


            # 简单判断

            if pitch < -10:

                state = "LOOK DOWN"

            else:

                state = "LOOK UP"


            cv2.putText(
                frame,
                state,
                (20,110),
                cv2.FONT_HERSHEY_SIMPLEX,
                1,
                (0,0,255),
                2
            )


    cv2.imshow(
        "Head Pose",
        frame
    )


    if cv2.waitKey(1)==27:
        break


cap.release()
cv2.destroyAllWindows()