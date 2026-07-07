const DEFAULT_MAX_BYTES = 300 * 1024

function canvasBlob(canvas, type, quality) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('图片压缩失败')), type, quality)
  })
}

async function loadImage(file) {
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(file)
    return { image: bitmap, width: bitmap.width, height: bitmap.height, dispose: () => bitmap.close() }
  }
  const url = URL.createObjectURL(file)
  const image = new Image()
  await new Promise((resolve, reject) => {
    image.onload = resolve
    image.onerror = () => reject(new Error('无法读取图片'))
    image.src = url
  })
  return { image, width: image.naturalWidth, height: image.naturalHeight, dispose: () => URL.revokeObjectURL(url) }
}

export async function compressImageFile(file, maxBytes = DEFAULT_MAX_BYTES) {
  if (!file || file.size <= maxBytes) return file
  if (!String(file.type || '').startsWith('image/')) throw new Error('请选择图片文件')

  const source = await loadImage(file)
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d', { alpha: false })
  if (!context) {
    source.dispose()
    throw new Error('当前浏览器不支持图片压缩')
  }

  let width = source.width
  let height = source.height
  let quality = 0.88
  try {
    for (let attempt = 0; attempt < 18; attempt += 1) {
      canvas.width = Math.max(1, Math.round(width))
      canvas.height = Math.max(1, Math.round(height))
      context.fillStyle = '#fff'
      context.fillRect(0, 0, canvas.width, canvas.height)
      context.drawImage(source.image, 0, 0, canvas.width, canvas.height)
      const blob = await canvasBlob(canvas, 'image/webp', quality)
      if (blob.size <= maxBytes) {
        const baseName = String(file.name || 'venue-image').replace(/\.[^.]+$/, '')
        return new File([blob], `${baseName}.webp`, { type: blob.type, lastModified: Date.now() })
      }
      const scale = Math.min(0.88, Math.sqrt(maxBytes / blob.size) * 0.94)
      width = Math.max(1, canvas.width * scale)
      height = Math.max(1, canvas.height * scale)
      quality = Math.max(0.42, quality - 0.06)
    }
  } finally {
    source.dispose()
  }
  throw new Error('图片内容过于复杂，无法压缩到300KB以内')
}

export { DEFAULT_MAX_BYTES }
