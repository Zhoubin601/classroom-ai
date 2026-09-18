import cv2
import numpy as np
import mediapipe as mp

from pathlib import Path
from ultralytics import YOLO

from mediapipe.tasks import python
from mediapipe.tasks.python import vision

if __package__:
    from .paths import MODELS_DIR
else:
    from paths import MODELS_DIR

# ==========================
# 初始化 YOLO
# ==========================

import torch

device = 0 if torch.cuda.is_available() else "cpu"

print("YOLO device:", device)

yolo_model = YOLO(str(MODELS_DIR / "yolo11n.pt"))


# ==========================
# 初始化 MediaPipe
# ==========================

base_options = python.BaseOptions(
    model_asset_path=str(MODELS_DIR / "face_landmarker.task")
)


options = vision.FaceLandmarkerOptions(
    base_options=base_options,
    num_faces=10
)


face_detector = vision.FaceLandmarker.create_from_options(
    options
)


# ==========================
# Head Pose 参数
# ==========================


face_3d_model = np.array([

    [0.0,0.0,0.0],          # 鼻尖
    [0.0,-63.6,-12.5],      # 下巴
    [-43.3,32.7,-26],       # 左眼
    [43.3,32.7,-26],        # 右眼
    [-28.9,-28.9,-24.1],    # 左嘴角
    [28.9,-28.9,-24.1]

],dtype=np.float64)


landmark_ids = [
    1,
    152,
    33,
    263,
    61,
    291
]


# ==========================
# 摄像头
# ==========================

cap = cv2.VideoCapture(
    0,
    cv2.CAP_DSHOW
)


while True:


    ret, frame = cap.read()

    if not ret:
        break


    h,w,_ = frame.shape


    # ======================
    # YOLO检测
    # ======================

    results = yolo_model(
        frame,
        device=0,
        verbose=False
    )


    person_count = 0
    look_up_count = 0



    for result in results:


        boxes = result.boxes


        for box in boxes:


            cls = int(box.cls[0])


            # 只检测person
            if cls != 0:
                continue


            person_count += 1


            x1,y1,x2,y2 = map(
                int,
                box.xyxy[0]
            )


            # 防止越界

            x1=max(0,x1)
            y1=max(0,y1)
            x2=min(w,x2)
            y2=min(h,y2)



            # 截取人区域

            person_img = frame[
                y1:y2,
                x1:x2
            ]


            if person_img.size==0:
                continue



            # ==================
            # MediaPipe检测脸
            # ==================

            rgb = cv2.cvtColor(
                person_img,
                cv2.COLOR_BGR2RGB
            )


            mp_image = mp.Image(
                image_format=
                mp.ImageFormat.SRGB,
                data=rgb
            )


            face_result = (
                face_detector.detect(mp_image)
            )



            state="UNKNOWN"



            if face_result.face_landmarks:


                face = (
                    face_result.face_landmarks[0]
                )


                face_2d=[]


                for idx in landmark_ids:


                    lm=face[idx]


                    fx=int(
                        lm.x*(x2-x1)
                    )

                    fy=int(
                        lm.y*(y2-y1)
                    )


                    face_2d.append(
                        [fx,fy]
                    )



                face_2d=np.array(
                    face_2d,
                    dtype=np.float64
                )



                # 摄像头参数

                focal_length=w

                center=(
                    w/2,
                    h/2
                )


                camera_matrix=np.array(
                    [
                    [focal_length,0,center[0]],
                    [0,focal_length,center[1]],
                    [0,0,1]
                    ],
                    dtype=np.float64
                )


                dist=np.zeros(
                    (4,1)
                )



                success,rotation_vec,_ = (
                    cv2.solvePnP(
                        face_3d_model,
                        face_2d,
                        camera_matrix,
                        dist
                    )
                )


                if success:


                    rotation_matrix,_=(
                        cv2.Rodrigues(
                            rotation_vec
                        )
                    )


                    angles,*_=(
                        cv2.RQDecomp3x3(
                            rotation_matrix
                        )
                    )


                    pitch=angles[0]*360



                    # 判断

                    if pitch > -10:

                        state="UP"
                        look_up_count+=1

                    else:

                        state="DOWN"



            # 画框

            cv2.rectangle(
                frame,
                (x1,y1),
                (x2,y2),
                (0,255,0),
                2
            )


            cv2.putText(
                frame,
                state,
                (x1,y1-10),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.8,
                (0,0,255),
                2
            )



    # ======================
    # 统计
    # ======================


    rate=0


    if person_count>0:

        rate=(
            look_up_count
            /
            person_count
            *
            100
        )


    cv2.putText(
        frame,
        f"People:{person_count}",
        (20,40),
        cv2.FONT_HERSHEY_SIMPLEX,
        1,
        (0,255,0),
        2
    )


    cv2.putText(
        frame,
        f"LookUp Rate:{rate:.1f}%",
        (20,80),
        cv2.FONT_HERSHEY_SIMPLEX,
        1,
        (0,255,0),
        2
    )



    cv2.imshow(
        "Classroom AI",
        frame
    )



    if cv2.waitKey(1)==27:
        break



cap.release()
cv2.destroyAllWindows()
