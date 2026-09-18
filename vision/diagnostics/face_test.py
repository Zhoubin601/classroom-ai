import cv2
import mediapipe as mp

from pathlib import Path
from mediapipe.tasks import python
from mediapipe.tasks.python import vision


# 模型路径
model_path = str(Path(__file__).resolve().parent.parent / "models" / "face_landmarker.task")


# 创建模型
base_options = python.BaseOptions(
    model_asset_path=model_path
)


options = vision.FaceLandmarkerOptions(
    base_options=base_options,
    num_faces=5
)


detector = vision.FaceLandmarker.create_from_options(
    options
)


# 摄像头
cap = cv2.VideoCapture(
    0,
    cv2.CAP_DSHOW
)


while True:

    ret, frame = cap.read()

    if not ret:
        break


    # OpenCV BGR -> MediaPipe Image
    rgb = cv2.cvtColor(
        frame,
        cv2.COLOR_BGR2RGB
    )


    mp_image = mp.Image(
        image_format=mp.ImageFormat.SRGB,
        data=rgb
    )


    # 人脸检测
    result = detector.detect(mp_image)


    if result.face_landmarks:

        for face in result.face_landmarks:

            h, w, _ = frame.shape


            for landmark in face:

                x = int(landmark.x * w)
                y = int(landmark.y * h)


                cv2.circle(
                    frame,
                    (x,y),
                    1,
                    (0,255,0),
                    -1
                )


    cv2.imshow(
        "Face Mesh",
        frame
    )


    if cv2.waitKey(1) == 27:
        break


cap.release()
cv2.destroyAllWindows()