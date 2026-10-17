// Shared by initial uploads and resource version uploads.
export function parseResourceUploadPolicy(value) {
  if (!Number.isSafeInteger(value?.maxFileBytes) || value.maxFileBytes < 0 || !Array.isArray(value.allowedExtensions)) {
    throw new Error('上传规则无效，请重新加载')
  }
  const allowedExtensions = [...new Set(value.allowedExtensions.map(extension => {
    if (typeof extension !== 'string' || !/^\.?[a-z0-9]+$/i.test(extension.trim())) throw new Error('上传规则无效，请重新加载')
    return extension.trim().replace(/^\./, '').toLowerCase()
  }))]
  return { maxFileBytes: value.maxFileBytes, allowedExtensions }
}

export function formatUploadLimit(bytes) {
  const unit = bytes >= 1024 * 1024 ? 'MiB' : bytes >= 1024 ? 'KiB' : '字节'
  const divisor = unit === 'MiB' ? 1024 * 1024 : unit === 'KiB' ? 1024 : 1
  const amount = Number((bytes / divisor).toFixed(3))
  return unit === '字节' ? `${bytes} 字节` : `${amount} ${unit}（${bytes} 字节）`
}

export function validateResourceUpload(file, policy) {
  if (!policy) return '上传规则尚未就绪，请重试加载'
  if (!file || file.size === 0) return '请选择非空文件'
  if (file.size > policy.maxFileBytes) return `单文件大小不能超过 ${formatUploadLimit(policy.maxFileBytes)}`
  const filename = file.name.trim()
  const extension = filename.includes('.') ? filename.slice(filename.lastIndexOf('.') + 1).trim().toLowerCase() : ''
  if (!policy.allowedExtensions.includes(extension)) {
    return policy.allowedExtensions.length ? `不支持的文件格式，允许：${policy.allowedExtensions.map(item => item.toUpperCase()).join(' / ')}` : '当前未开放可上传的文件格式'
  }
  return ''
}
