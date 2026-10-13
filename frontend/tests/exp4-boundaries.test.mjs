import test from 'node:test'
import assert from 'node:assert/strict'
import { linkCancellation, abortableDelay } from '../src/api/cancellation.mjs'
import { validateWeights } from '../src/ui/weights.mjs'

test('valid decimal templates sum exactly in thousandths', () => {
  for (const values of [[33.3,33.3,0.1,33.3],[30.1,30.1,30.1,9.7],[33.333,33.333,33.334,0],[0,100,0,0]])
    assert.deepEqual(validateWeights(values), {valid:true,total:100,message:''})
  for (const values of [[25,25,25,24.999],[-1,26,25,50],[0.0001,0,0,99.9999],['',25,25,50],[NaN,25,25,50]])
    assert.equal(validateWeights(values).valid, false)
})

test('caller and session cancellation both reach the transport', () => {
  const caller=new AbortController(),request=linkCancellation(caller.signal)
  caller.abort('leave-page');assert.equal(request.controller.signal.aborted,true);assert.equal(request.controller.signal.reason,'leave-page');request.dispose()
  const session=linkCancellation(new AbortController().signal)
  session.controller.abort();assert.equal(session.controller.signal.aborted,true);session.dispose()
  const cancelled=new AbortController();cancelled.abort()
  const early=linkCancellation(cancelled.signal);assert.equal(early.controller.signal.aborted,true);early.dispose()
})

test('settled requests detach their cancellation listeners', () => {
  const caller=new AbortController(),request=linkCancellation(caller.signal)
  request.dispose();caller.abort();assert.equal(request.controller.signal.aborted,false)
})

test('camera polling delay cancels immediately and also completes normally', async () => {
  const caller=new AbortController(),waiting=abortableDelay(10000,caller.signal)
  caller.abort();await assert.rejects(waiting,e=>e.name==='AbortError')
  await assert.doesNotReject(abortableDelay(1,new AbortController().signal))
})
