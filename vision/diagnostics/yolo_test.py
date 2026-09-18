import cv2
from pathlib import Path
from ultralytics import YOLO


model = YOLO(str(Path(__file__).resolve().parent.parent / "models" / "yolo11n.pt"))


cap = cv2.VideoCapture(0)


while True:

    ret, frame = cap.read()

    if not ret:
        break


    results = model(frame)


    annotated = results[0].plot()


    cv2.imshow(
        "YOLO",
        annotated
    )


    if cv2.waitKey(1)==27:
        break


cap.release()
cv2.destroyAllWindows()