import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Image, Result, Skeleton, Space, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { Link, useParams } from 'react-router-dom'
import * as api from '../api'
import type { Artwork } from '../types'

type ImageKind = 'original' | 'hd'

export default function ArtworkImageViewerPage() {
  const { id, kind } = useParams<{ id: string; kind: ImageKind }>()
  const artworkId = Number(id)
  const imageKind = kind === 'hd' ? 'hd' : kind === 'original' ? 'original' : undefined
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [imageUrl, setImageUrl] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()

  const imageLabel = imageKind === 'hd' ? '超清无损图' : '原图'
  const sourceUrl = useMemo(() => {
    if (!Number.isFinite(artworkId) || !imageKind) return undefined
    return imageKind === 'hd'
      ? api.hdImageViewUrl(artworkId)
      : api.originalImageViewUrl(artworkId)
  }, [artworkId, imageKind])

  useEffect(() => {
    if (!sourceUrl || !Number.isFinite(artworkId)) {
      setError('图片地址无效')
      setLoading(false)
      return
    }

    let objectUrl: string | undefined
    let cancelled = false
    setLoading(true)
    setError(undefined)

    Promise.all([
      api.getArtwork(artworkId),
      api.createProtectedBlobUrl(sourceUrl),
    ])
      .then(([artworkData, protectedUrl]) => {
        objectUrl = protectedUrl
        if (cancelled) {
          URL.revokeObjectURL(protectedUrl)
          return
        }
        setArtwork(artworkData)
        setImageUrl(protectedUrl)
      })
      .catch((e: any) => {
        if (!cancelled) setError(e.message || '图片加载失败')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [artworkId, sourceUrl])

  if (loading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (error || !imageUrl) {
    return (
      <Result
        status="warning"
        title={error || '图片加载失败'}
        extra={<Button icon={<ArrowLeftOutlined />} onClick={() => window.close()}>关闭</Button>}
      />
    )
  }

  return (
    <Card
      title={
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => window.close()} />
          <Typography.Title level={4} style={{ margin: 0 }}>
            {artwork?.title || `艺术品 ${artworkId}`} - {imageLabel}
          </Typography.Title>
        </Space>
      }
      extra={
        <Space>
          <Link to={`/artworks/${artworkId}`}>返回详情</Link>
        </Space>
      }
    >
      {imageKind === 'hd' && artwork?.hdImageStorageType && (
        <Alert
          type="info"
          showIcon
          message={`存储位置：${artwork.hdImageStorageType === 'OBJECT' ? '对象存储' : artwork.hdImageStorageType === 'LOCAL_OBJECT' ? '本地 + 对象存储' : '本地'}`}
          style={{ marginBottom: 16 }}
        />
      )}
      <div style={{ minHeight: 480, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fff' }}>
        <Image
          src={imageUrl}
          alt={`${artwork?.title || `艺术品 ${artworkId}`} ${imageLabel}`}
          style={{ maxWidth: '100%', maxHeight: 'calc(100vh - 260px)', objectFit: 'contain' }}
        />
      </div>
    </Card>
  )
}
