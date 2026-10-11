import ast,json,time,threading,urllib.request,urllib.error
from pathlib import Path
from types import SimpleNamespace
root=Path(r'C:/Users/a3185/.codex/worktrees/8810/classroom-ai-demo')
source=root/'vision/classroom_monitor.py'
tree=ast.parse(source.read_text(encoding='utf-8-sig'))
method=next(n for n in ast.walk(tree) if isinstance(n,ast.FunctionDef) and n.name=='async_send_stream_report')
module=ast.fix_missing_locations(ast.Module(body=[method],type_ignores=[]))
observations=[]
original=urllib.request.urlopen
def observe(req,*args,**kwargs):
    row={'url':req.full_url,'method':req.get_method(),'hasAuthorization':req.has_header('Authorization'),'hasOfferingId':'offeringId' in json.loads(req.data),'payloadKeys':list(json.loads(req.data))}
    observations.append(row)
    try:
        response=original(req,*args,**kwargs);row['http']=response.status;return response
    except urllib.error.HTTPError as e:
        row['http']=e.code;row['response']=e.read().decode('utf-8');raise
urllib.request.urlopen=observe
namespace={'json':json,'urllib':urllib,'threading':threading}
exec(compile(module,str(source),'exec'),namespace)
monitor=SimpleNamespace(backend_url='http://127.0.0.1:18081',is_reporting=False)
payload={'sessionId':'CLASS_LIVE_MONITOR','courseName':'transport-probe-synthetic','className':'transport-probe-synthetic','detectedPersonCount':0,'lookupCount':0,'lookdownCount':0,'lookupRate':0,'presentStudentIds':[],'studentPoses':{}}
namespace['async_send_stream_report'](monitor,payload)
for i in range(80):
    if observations and not monitor.is_reporting:break
    time.sleep(.1)
assert observations and observations[0]['http']==401
assert not observations[0]['hasAuthorization'] and not observations[0]['hasOfferingId']
result={'result':'REPRODUCED_INTEGRATION_GAP','methodSource':'vision/classroom_monitor.py','methodLine':method.lineno,'execution':'Actual native asynchronous sender method extracted via AST to avoid GPU imports; unmodified HTTP transport sent to real isolated backend. Only backend_url redirected. Synthetic frame input; no camera inference performed.','observations':observations,'silentFailure':not monitor.is_reporting,'cameraHardware':'NOT_TESTED'}
Path(__file__).with_name('vision-transport-results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print('REPRODUCED camera native sender HTTP 401, no authentication, no offeringId; error swallowed')
