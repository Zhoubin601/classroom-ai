<template>
  <section ref="root" tabindex="0" aria-label="课件 PDF 预览" data-testid="protected-pdf-preview"
    class="select-none space-y-3 outline-none" @contextmenu.prevent @copy.prevent @cut.prevent @dragstart.prevent>
    <div class="flex items-center justify-between gap-3 text-xs text-slate-600">
      <span>限时只读预览 · 身份水印保护</span>
      <div class="flex items-center gap-3">
        <button aria-label="上一页" :disabled="busy || pageNumber <= 1" @click="turn(-1)" class="px-3 py-2 border rounded-lg disabled:opacity-40">上一页</button>
        <span data-testid="pdf-page-counter">第 {{ pageNumber }} / {{ pageCount }} 页</span>
        <button aria-label="下一页" :disabled="busy || pageNumber >= pageCount" @click="turn(1)" class="px-3 py-2 border rounded-lg disabled:opacity-40">下一页</button>
      </div>
    </div>
    <p v-if="error" role="alert" class="text-rose-600">{{ error }}</p>
    <p v-else-if="busy" role="status" class="text-slate-500">正在加载课件…</p>
    <div class="max-h-[65vh] overflow-auto bg-slate-100 rounded-xl border p-3">
      <canvas ref="canvas" data-testid="pdf-page-canvas" :aria-label="`课件第${pageNumber}页`" role="img" class="block mx-auto max-w-full h-auto shadow" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, nextTick } from 'vue'
import { getDocument, GlobalWorkerOptions } from 'pdfjs-dist'
import type { PDFDocumentProxy, RenderTask } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

GlobalWorkerOptions.workerSrc = workerUrl
const props = defineProps<{ src: string }>()
const root = ref<HTMLElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const pageNumber = ref(1)
const pageCount = ref(0)
const busy = ref(true)
const error = ref('')
let document: PDFDocumentProxy | undefined
let task: RenderTask | undefined
let loader: ReturnType<typeof getDocument> | undefined
let generation = 0

// Capture document shortcuts while the viewer is mounted, including when a
// surrounding modal button has focus. No text layer or native PDF toolbar exists.
const guard = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && ['c', 'x', 's', 'p', 'a'].includes(event.key.toLowerCase())) {
    event.preventDefault()
    event.stopImmediatePropagation()
  }
}
window.addEventListener('keydown', guard, true)
async function render() {
  if (!document || !canvas.value) return
  const version = generation
  busy.value = true
  try {
    const page = await document.getPage(pageNumber.value)
    if (version !== generation || !canvas.value) return
    const viewport = page.getViewport({ scale: Math.min(2, 1000 / page.getViewport({scale:1}).width) })
    canvas.value.width = Math.ceil(viewport.width)
    canvas.value.height = Math.ceil(viewport.height)
    task = page.render({canvasContext: canvas.value.getContext('2d')!, viewport})
    await task.promise
  } catch (e: any) {
    if (version === generation && e.name !== 'RenderingCancelledException') error.value = '课件渲染失败，请关闭后重新申请预览'
  } finally { if (version === generation) busy.value = false }
}
async function turn(delta: number) {
  if (busy.value) return
  pageNumber.value = Math.max(1, Math.min(pageCount.value, pageNumber.value + delta))
  await render()
}
watch(() => props.src, async src => {
  const version = ++generation
  task?.cancel()
  await loader?.destroy()
  document = undefined
  error.value = ''; busy.value = true; pageNumber.value = 1; pageCount.value = 0
  if (!src || version !== generation) return
  try {
    loader = getDocument({url:src, isEvalSupported:false})
    const loaded = await loader.promise
    if (version !== generation) { await loaded.destroy(); return }
    document = loaded; pageCount.value = loaded.numPages
    await nextTick()
    await render()
    root.value?.focus()
  } catch (e: any) {
    if (version === generation) { error.value = '课件加载失败，请关闭后重新申请预览'; busy.value = false }
  }
}, {immediate:true})
onUnmounted(() => {
  generation++
  task?.cancel()
  void loader?.destroy()
  window.removeEventListener('keydown', guard, true)
})
</script>
