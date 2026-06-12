import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Result, Skeleton, Space, Typography } from 'antd'
import { ArrowLeftOutlined, ReloadOutlined, ZoomInOutlined, ZoomOutOutlined } from '@ant-design/icons'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import * as api from '../../api'

type ImageKind = 'original' | 'hd'

const imageTitle = (kind?: ImageKind) => kind === 'hd' ? '高清大图' : '原图'

const businessError = (message?: string, kind?: ImageKind) => {
  if (kind !== 'hd') return message || '图片加载失败'
  const text = message || ''
  if (text.includes('权限')) return '当前账号没有高清图查看权限'
  if (text.includes('artCode') || text.includes('识别')) return '作品缺少高清图识别信息'
  if (text.includes('尚未') || text.includes('未同步')) return '高清图尚未同步'
  if (text.includes('不存在') || text.includes('not found') || text.includes('404')) return '高清图文件不存在'
  if (text.includes('超时') || text.toLowerCase().includes('timeout')) return '请求超时，请稍后重试'
  return '高清图读取失败，请稍后重试'
}

export default function MobileArtworkImageViewerPage() {
  const { id, kind } = useParams<{ id: string; kind: ImageKind }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const artworkId = Number(id)
  const imageKind = kind === 'hd' ? 'hd' : 'original'
  const [imageUrl, setImageUrl] = useState<string>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [scale, setScale] = useState(1)
  const [reloadKey, setReloadKey] = useState(0)

  const from = searchParams.get('from') || (Number.isFinite(artworkId) ? `/m/artworks/${artworkId}` : '/m/artworks')
  const sourceUrl = useMemo(() => {
    if (!Number.isFinite(artworkId)) return undefined
    return imageKind === 'hd' ? api.hdImageV2ViewUrl(artworkId) : api.originalImageViewUrl(artworkId)
  }, [artworkId, imageKind])

  const loadImage = useCallback(() => {
    if (!sourceUrl) {
      setError('图片地址无效')
      setLoading(false)
      return () => {}
    }
    let objectUrl: string | undefined
    let cancelled = false
    setLoading(true)
    setError(undefined)
    setScale(1)
    api.createProtectedBlobUrl(sourceUrl)
      .then((url) => {
        objectUrl = url
        if (cancelled) {
          URL.revokeObjectURL(url)
          return
        }
        setImageUrl(url)
      })
      .catch((e: any) => {
        if (!cancelled) setError(businessError(e.message, imageKind))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [imageKind, sourceUrl])

  useEffect(() => loadImage(), [loadImage, reloadKey])

  const retry = () => {
    if (imageUrl) {
      URL.revokeObjectURL(imageUrl)
      setImageUrl(undefined)
    }
    setReloadKey((value) => value + 1)
  }

  return (
    <MobileDataLayout title={imageTitle(imageKind)} hideNav>
      <div className="mobile-image-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(from)}>返回详情</Button>
        {!loading && !error && (
          <Space>
            <Button icon={<ZoomOutOutlined />} onClick={() => setScale((value) => Math.max(0.5, value - 0.25))} />
            <Button icon={<ZoomInOutlined />} onClick={() => setScale((value) => Math.min(4, value + 0.25))} />
          </Space>
        )}
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : error || !imageUrl ? (
        <Result
          status="warning"
          title={`暂无法查看${imageTitle(imageKind)}`}
          subTitle={error}
          extra={[
            <Button key="back" onClick={() => navigate(from)}>返回详情</Button>,
            <Button key="retry" type="primary" icon={<ReloadOutlined />} onClick={retry}>重试</Button>,
          ]}
        />
      ) : (
        <div
          className="mobile-image-stage"
          onContextMenu={(event) => event.preventDefault()}
        >
          <img
            src={imageUrl}
            alt={imageTitle(imageKind)}
            draggable={false}
            style={{ transform: `scale(${scale})` }}
          />
        </div>
      )}

      <Typography.Text type="secondary" className="mobile-image-note">
        图片仅供查看，当前页面不提供下载。
      </Typography.Text>
    </MobileDataLayout>
  )
}
