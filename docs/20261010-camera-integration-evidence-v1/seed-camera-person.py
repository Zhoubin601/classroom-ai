"""Enroll an in-memory physical-camera embedding only in the disposable database."""
import contextlib, json, os, sys, time, urllib.request
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../..', 'vision')))
with open(os.devnull, 'w') as null, contextlib.redirect_stdout(null), contextlib.redirect_stderr(null):
    import classroom_monitor
    import cv2
    import insightface
    app = insightface.app.FaceAnalysis(name='buffalo_l', providers=['CUDAExecutionProvider','CPUExecutionProvider'])
    app.prepare(ctx_id=0, det_size=(640,640))
    cap = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    vector = None
    deadline=time.time()+20
    try:
        while cap.isOpened() and time.time()<deadline:
            ok, frame = cap.read()
            if not ok: continue
            faces=app.get(frame)
            if faces:
                face=max(faces,key=lambda f:(f.bbox[2]-f.bbox[0])*(f.bbox[3]-f.bbox[1]))
                vector=face.normed_embedding.astype(float).tolist()
                break
    finally: cap.release()
if vector is None:
    print(json.dumps({'faceDetected':False,'featureDimension':0}));sys.exit(2)
base=os.environ['BACKEND_URL'];student=os.environ['CAMERA_TEST_STUDENT']
def post(route, data):
    request=urllib.request.Request(base+route,data=json.dumps(data).encode(),headers={'Authorization':os.environ['CAMERA_TEST_AUTH'],'Content-Type':'application/json'})
    with urllib.request.urlopen(request,timeout=10) as response:
        assert json.loads(response.read())['code']==200
post('/api/student',{'studentId':student,'name':'现场合成测试人','className':'摄像头隔离验收班'})
post('/api/face/register',{'studentId':student,'name':'现场合成测试人','className':'摄像头隔离验收班','featureVector':vector})
print(json.dumps({'faceDetected':True,'featureDimension':len(vector),'cameraIndex':0,'storedOnlyInDisposableDatabase':True}))
