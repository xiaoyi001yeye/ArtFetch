import { useEffect, useState } from 'react'
import { Image, Skeleton, Typography } from 'antd'
import { PictureOutlined } from '@ant-design/icons'
import * as api from '../../api'

export function ProtectedPreviewImage({ url, alt }: { url?: string; alt: string }) {
  const [objectUrl, setObjectUrl] = useState<string>()
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    let loadedUrl: string | undefined
    setObjectUrl(undefined)
    setFailed(false)
    if (!url) return
    api.createProtectedBlobUrl(url)
      .then((value) => {
        loadedUrl = value
        if (active) setObjectUrl(value)
        else URL.revokeObjectURL(value)
      })
      .catch(() => active && setFailed(true))
    return () => {
      active = false
      if (loadedUrl) URL.revokeObjectURL(loadedUrl)
    }
  }, [url])

  if (!url || failed) {
    return <PictureOutlined style={{ color: '#a3a3a3', fontSize: 26 }} />
  }
  if (!objectUrl) {
    return <Skeleton.Image active />
  }
  return <img src={objectUrl} alt={alt} />
}

export function ProtectedImageViewer({
  url,
  title,
  open,
  onClose,
}: {
  url?: string
  title: string
  open: boolean
  onClose: () => void
}) {
  const [objectUrl, setObjectUrl] = useState<string>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    let active = true
    let loadedUrl: string | undefined
    if (!open || !url) return
    setError(undefined)
    api.createProtectedBlobUrl(url)
      .then((value) => {
        loadedUrl = value
        if (active) setObjectUrl(value)
        else URL.revokeObjectURL(value)
      })
      .catch((e: Error) => active && setError(e.message))
    return () => {
      active = false
      if (loadedUrl) URL.revokeObjectURL(loadedUrl)
      setObjectUrl(undefined)
    }
  }, [open, url])

  if (!open) return null
  if (error) {
    return <Typography.Text type="danger">{error}</Typography.Text>
  }
  if (!objectUrl) return <Skeleton.Image active />
  return (
    <Image
      src={objectUrl}
      alt={title}
      style={{ display: 'none' }}
      preview={{
        visible: true,
        src: objectUrl,
        movable: true,
        minScale: 0.2,
        maxScale: 8,
        scaleStep: 0.5,
        destroyOnHidden: true,
        onVisibleChange: (visible) => {
          if (!visible) onClose()
        },
      }}
    />
  )
}
