export function checkApiResponse(response) {
  if (response.data && typeof response.data.code === 'number' && response.data.code !== 200) {
    throw Object.assign(new Error(response.data.message || '请求失败'), { response })
  }
  return response
}

export function rejectApiError(error) {
  error.message = error.response?.data?.message || error.message || '请求失败'
  return Promise.reject(error)
}
