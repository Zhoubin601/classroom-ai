import { computed, onMounted, onUnmounted, ref } from 'vue'
import { resourceApi } from '../api'
import type { ResourceUploadPolicy } from '../api/types'
import { formatUploadLimit, parseResourceUploadPolicy, validateResourceUpload } from './resourceUploadPolicy.mjs'

export function useResourceUploadPolicy() {
  const policy = ref<ResourceUploadPolicy | null>(null)
  const loading = ref(false)
  const error = ref('')
  let disposed = false, request = 0
  let controller: AbortController | undefined
  const ready = computed(() => !!policy.value && !loading.value && !error.value)
  const accept = computed(() => policy.value?.allowedExtensions.map(extension => '.' + extension).join(',') || '')
  const allowedLabel = computed(() => policy.value?.allowedExtensions.map(extension => extension.toUpperCase()).join(' / ') || '暂无允许的格式')
  const limitLabel = computed(() => policy.value ? formatUploadLimit(policy.value.maxFileBytes) : '')
  const description = computed(() => ready.value ? `单文件上限 ${limitLabel.value}；支持 ${allowedLabel.value}` : loading.value ? '正在读取上传规则…' : '上传规则暂不可用')
  async function reload() {
    if (disposed) return
    const current = ++request
    controller?.abort()
    const pending = new AbortController()
    controller = pending
    loading.value = true
    error.value = ''
    policy.value = null
    try {
      const result = parseResourceUploadPolicy(await resourceApi.getUploadPolicy(pending.signal))
      if (!disposed && current === request) policy.value = result
    } catch {
      if (!disposed && current === request && !pending.signal.aborted) error.value = '上传规则加载失败，请重试'
    } finally {
      if (!disposed && current === request) { loading.value = false; controller = undefined }
    }
  }
  const validate = (file: File) => ready.value ? validateResourceUpload(file, policy.value) : '上传规则尚未就绪，请重试加载'
  onMounted(() => { void reload() })
  onUnmounted(() => { disposed = true; request++; controller?.abort() })
  return { policy, loading, error, ready, accept, allowedLabel, limitLabel, description, reload, validate }
}
