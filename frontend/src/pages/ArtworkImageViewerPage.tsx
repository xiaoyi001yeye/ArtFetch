import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Image, Result, Skeleton, Space, Typography } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { Link, useParams } from 'react-router-dom'
import * as api from '../api'
import type { Artwork } from '../types'

type ImageKind = 'original' | 'hd' | 'hd-v2'

export default function ArtworkImageViewerPage() {
  const { id, kind } = useParams<{ id: string; kind: ImageKind }>()
  const artworkId = Number(id)
  const imageKind = kind === 'hd-v2' ? 'hd-v2' : kind === 'hd' ? 'hd' : kind === 'original' ? 'original' : undefined
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [imageUrl, setImageUrl] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [errorDetail, setErrorDetail] = useState<string>()

  const imageLabel = imageKind === 'hd-v2' ? '高清大图 V2' : imageKind === 'hd' ? '超清无损图' : '原图'
  const sourceUrl = useMemo(() => {
    if (!Number.isFinite(artworkId) || !imageKind) return undefined
    if (imageKind === 'hd-v2') return api.hdImageV2ViewUrl(artworkId)
    if (imageKind === 'hd') return api.hdImageViewUrl(artworkId)
    return api.originalImageViewUrl(artworkId)
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
    setErrorDetail(undefined)

    Promise.allSettled([
      api.getArtwork(artworkId),
      api.createProtectedBlobUrl(sourceUrl),
    ])
      .then(([artworkResult, imageResult]) => {
        const artworkData = artworkResult.status === 'fulfilled' ? artworkResult.value : null
        if (artworkData && !cancelled) setArtwork(artworkData)

        if (imageResult.status === 'rejected') {
          if (!cancelled) {
            const baseMessage = imageResult.reason?.message || '图片加载失败'
            setError(baseMessage)
            setErrorDetail(buildImageErrorDetail(imageKind, artworkId, artworkData))
          }
          return
        }

        objectUrl = imageResult.value
        if (cancelled) {
          URL.revokeObjectURL(imageResult.value)
          return
        }
        if (!artworkData && artworkResult.status === 'rejected') {
          setError(artworkResult.reason?.message || '艺术品信息加载失败')
          setErrorDetail('图片请求已返回，但艺术品详情加载失败。请刷新页面或检查详情接口。')
          URL.revokeObjectURL(imageResult.value)
          return
        }
        setArtwork(artworkData)
        setImageUrl(imageResult.value)
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
        subTitle={errorDetail}
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
      {imageKind === 'hd-v2' && (
        <Alert
          type="info"
          showIcon
          message="V2 canonical TOS 读取"
          description="当前图片只按 sourceProvider=artron 和 artCode 计算固定 TOS 路径读取，不依赖本机磁盘路径或旧对象路径。"
          style={{ marginBottom: 16 }}
        />
      )}
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

const storageTypeText = (storageType?: string) => {
  if (storageType === 'OBJECT') return '对象存储'
  if (storageType === 'LOCAL_OBJECT') return '本地 + 对象存储'
  return '本地'
}

const buildImageErrorDetail = (imageKind: ImageKind | undefined, artworkId: number, artwork: Artwork | null) => {
  if (imageKind !== 'hd') {
    if (imageKind === 'hd-v2') {
      const identity = artwork?.externalId || artwork?.sourceUrl || '未拿到 externalId/sourceUrl'
      return `请确认该作品已经完成 V2 canonical TOS 升级，且生产/测试环境使用同一 TOS bucket。作品 ID：${artworkId}；身份参数：${identity}。`
    }
    return `请检查图片补充任务状态与服务端日志，搜索 artworkId=${artworkId}。`
  }
  if (!artwork) {
    return `请检查服务端日志，搜索 artworkId=${artworkId}；重点查看 artfetch.image.storage-path、容器卷挂载、对象存储配置与数据库图片路径。`
  }
  const details = [
    `作品 ID：${artworkId}`,
    `超清图状态：${artwork.hdImageStatus || '未知'}`,
    `存储位置：${storageTypeText(artwork.hdImageStorageType)}`,
    `迁移状态：${artwork.hdImageMigrationStatus || '未知'}`,
  ]
  if (artwork.hdImageLastError) details.push(`补图错误：${artwork.hdImageLastError}`)
  if (artwork.hdImageMigrationLastError) details.push(`迁移错误：${artwork.hdImageMigrationLastError}`)
  details.push(`请检查服务端日志 artworkId=${artworkId}，重点核对生产容器存储挂载、artfetch.image.storage-path、数据库 hd_image_path 和对象存储配置。`)
  return details.join('；')
}
