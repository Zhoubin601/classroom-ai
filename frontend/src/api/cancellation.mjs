// Keep the caller's cancellation connected to the session-wide controller.
export function linkCancellation(signal) {
  const controller = new AbortController();
  const abort = () => controller.abort(signal?.reason);
  if (signal?.aborted) abort();
  else signal?.addEventListener?.('abort', abort, { once: true });
  return { controller, dispose: () => signal?.removeEventListener?.('abort', abort) };
}

export function abortableDelay(ms, signal) {
  return new Promise((resolve, reject) => {
    const abort = () => {
      clearTimeout(timer);
      signal?.removeEventListener?.('abort', abort);
      reject(new DOMException('操作已取消', 'AbortError'));
    };
    const timer = setTimeout(() => { signal?.removeEventListener?.('abort', abort); resolve(); }, ms);
    if (signal?.aborted) abort();
    else signal?.addEventListener?.('abort', abort, { once: true });
  });
}
