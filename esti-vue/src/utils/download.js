// 서버가 만든 파일을 그대로 내려받는다.
// 파일명은 서버가 Content-Disposition으로 정해 준다 — 양식·대상마다 규칙이 달라 화면이 짜맞추면 어긋난다.

/**
 * Content-Disposition에서 파일명을 꺼낸다.
 * 한글 파일명은 `filename*=UTF-8''...`(RFC 5987)로 오므로 그쪽을 먼저 본다.
 */
export function fileNameFrom(disposition, fallback) {
  if (!disposition) return fallback

  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim())
    } catch {
      // 인코딩이 깨졌으면 아래 일반 filename으로 넘어간다
    }
  }

  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1].trim() : fallback
}

/** blob 응답을 파일로 저장시킨다. */
export function saveBlob(response, fallbackName) {
  const blob = new Blob([response.data], {
    type: response.headers?.['content-type'] || 'application/octet-stream',
  })
  const name = fileNameFrom(response.headers?.['content-disposition'], fallbackName)

  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}
