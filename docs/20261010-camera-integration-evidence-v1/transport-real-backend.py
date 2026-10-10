import json, os, sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../../vision')))
from monitor_transport import MonitorTransport
t=MonitorTransport(os.environ['BACKEND_URL'],int(os.environ['CAMERA_TEST_OFFERING']),int(os.environ['CAMERA_TEST_SESSION']),authorization=os.environ['CAMERA_TEST_AUTH'])
metrics={'detectedPersonCount':2,'lookupCount':1,'lookdownCount':1,'lookupRate':0.5,
             'presentStudentIds':[os.environ['CAMERA_TEST_STUDENT'],'UNKNOWN_FACE_1'],
             'studentPoses':{os.environ['CAMERA_TEST_STUDENT']:'UP','UNKNOWN_FACE_1':'DOWN'}}
if os.environ.get('CAMERA_TEST_EMPTY')=='true':
    metrics={'detectedPersonCount':0,'lookupCount':0,'lookdownCount':0,'lookupRate':0,'presentStudentIds':[],'studentPoses':{}}
sent=t.send(metrics)
print(json.dumps({'sent':sent,**t.status()}))
