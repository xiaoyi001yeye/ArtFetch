import { useEffect, useMemo, useState } from 'react'
import { Button, Descriptions, Divider, Image, message, Modal, Skeleton, Space, Tag, Typography } from 'antd'
import { ArrowLeftOutlined, CloudOutlined, DollarOutlined, LinkOutlined, PictureOutlined } from '@ant-design/icons'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import MobileDataLayout from '../../layouts/MobileDataLayout'
import { useAuth } from '../../auth/AuthContext'
import { permissions } from '../../auth/permissions'
import * as api from '../../api'
import type { Artwork } from '../../types'

const display = (value?: string | null) => value && value.trim() ? value : '—'

const imageStatusText = (status?: string) => {
  if (status === 'DOWNLOADED') return '已保存'
  if (status === 'FAILED') return '失败'
  return '未保存'
}

const transactionStatusText = (status?: string) => ({
  HAS_PRICE: '已有成交价',
  MISSING: '待补充',
  LOGIN_REQUIRED: '需要登录',
  FAILED: '补充失败',
}[status || ''] || '待补充')

export default function MobileArtworkDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [loading, setLoading] = useState(true)
  const [supplementing, setSupplementing] = useState(false)

  const artworkId = Number(id)
  const from = searchParams.get('from') || '/m/artworks'
  const detailPath = `/m/artworks/${artworkId}?from=${encodeURIComponent(from)}`

  useEffect(() => {
    if (!Number.isFinite(artworkId)) {
      navigate('/m/artworks', { replace: true })
      return
    }
    setLoading(true)
    api.getArtwork(artworkId)
      .then(setArtwork)
      .catch((e: any) => {
        message.error(e.message)
        navigate('/m/artworks', { replace: true })
      })
      .finally(() => setLoading(false))
  }, [artworkId, navigate])

  const canViewOriginal = Boolean(artwork?.originalImageAvailable || artwork?.originalImageSourceUrl || artwork?.sourceUrl || artwork?.imageUrl)
  const canViewHd = useMemo(() => Boolean(artwork?.externalId || artwork?.sourceUrl?.match(/\/paimai-[^/?#]+/i)), [artwork])

  const handleBack = () => navigate(from)

  const handleSupplement = () => {
    if (!artwork) return
    Modal.confirm({
      title: '补充成交价',
      content: '将尝试从原始来源补充该艺术品成交价，并更新当前记录。',
      okText: '确认补充',
      cancelText: '取消',
      onOk: async () => {
        setSupplementing(true)
        try {
          const updated = await api.supplementTransactionPrice(artwork.id)
          setArtwork(updated)
          if (updated.transactionPrice) {
            message.success(`成交价已补充：${updated.transactionPrice}`)
          } else {
            message.info(`未获取到成交价：${updated.transactionPriceNote || '页面未提供'}`)
          }
        } catch (e: any) {
          message.error(`补充失败：${e.message}`)
        } finally {
          setSupplementing(false)
        }
      },
    })
  }

  if (loading) {
    return (
      <MobileDataLayout title="艺术品详情" hideNav>
        <Skeleton active paragraph={{ rows: 10 }} />
      </MobileDataLayout>
    )
  }

  if (!artwork) return null

  return (
    <MobileDataLayout title="艺术品详情" hideNav>
      <div className="mobile-detail-topbar">
        <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>返回列表</Button>
      </div>

      <section className="mobile-detail-hero">
        {artwork.imageUrl ? (
          <Image
            src={artwork.imageUrl}
            alt={artwork.title}
            preview={false}
            draggable={false}
            onContextMenu={(event) => event.preventDefault()}
          />
        ) : (
          <div className="mobile-detail-image-empty">暂无图片</div>
        )}
        <Typography.Title level={4}>{artwork.title}</Typography.Title>
        <div className="mobile-detail-meta">
          {display(artwork.artist)}{artwork.lotNumber ? ` / ${artwork.lotNumber}` : ''}
        </div>
        <div className="mobile-detail-meta">
          {[artwork.auctionHouse, artwork.auctionDate].filter(Boolean).join(' · ') || '拍卖信息待补充'}
        </div>
      </section>

      <section className="mobile-detail-actions">
        {canViewOriginal && hasPermission(permissions.artworkImageView) && (
          <Button icon={<PictureOutlined />} onClick={() => navigate(`/m/artworks/${artwork.id}/images/original?from=${encodeURIComponent(detailPath)}`)}>
            查看原图
          </Button>
        )}
        {hasPermission(permissions.artworkImageView) && (
          <Button
            type="primary"
            icon={<CloudOutlined />}
            disabled={!canViewHd}
            onClick={() => navigate(`/m/artworks/${artwork.id}/images/hd?from=${encodeURIComponent(detailPath)}`)}
          >
            查看高清大图
          </Button>
        )}
        {artwork.sourceUrl && (
          <Button icon={<LinkOutlined />} href={artwork.sourceUrl} target="_blank" rel="noreferrer">
            查看原始数据
          </Button>
        )}
      </section>
      {!canViewHd && hasPermission(permissions.artworkImageView) && (
        <div className="mobile-data-note">作品缺少高清图识别信息，暂不能读取高清大图。</div>
      )}

      <section className="mobile-detail-section">
        <Divider orientation="left">作品信息</Divider>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="材质">{display(artwork.medium)}</Descriptions.Item>
          <Descriptions.Item label="形制">{display(artwork.format)}</Descriptions.Item>
          <Descriptions.Item label="尺寸">{display(artwork.dimensions)}</Descriptions.Item>
        </Descriptions>
        <div className="mobile-detail-description">
          <Typography.Text type="secondary">拍品描述</Typography.Text>
          <Typography.Paragraph>{display(artwork.description)}</Typography.Paragraph>
        </div>
      </section>

      <section className="mobile-detail-section">
        <Divider orientation="left">价格信息</Divider>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="估价">{display(artwork.valuation)}</Descriptions.Item>
          <Descriptions.Item label="成交价">{display(artwork.transactionPrice)}</Descriptions.Item>
          <Descriptions.Item label="成交价状态">
            <Space wrap>
              <Tag>{transactionStatusText(artwork.transactionPriceStatus)}</Tag>
              {artwork.transactionPriceNote && <span>{artwork.transactionPriceNote}</span>}
            </Space>
          </Descriptions.Item>
        </Descriptions>
        {hasPermission(permissions.artworkTransactionPriceSupplement) && (
          <Button
            block
            type="primary"
            icon={<DollarOutlined />}
            loading={supplementing}
            onClick={handleSupplement}
            className="mobile-detail-primary-action"
          >
            补充成交价
          </Button>
        )}
      </section>

      <section className="mobile-detail-section">
        <Divider orientation="left">拍卖信息</Divider>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="拍卖会">{display(artwork.auctionName)}</Descriptions.Item>
          <Descriptions.Item label="拍卖专场">{display(artwork.auctionSession)}</Descriptions.Item>
          <Descriptions.Item label="拍卖地点">{display(artwork.auctionLocation)}</Descriptions.Item>
          <Descriptions.Item label="预展时间">{display(artwork.previewTime)}</Descriptions.Item>
          <Descriptions.Item label="预展地点">{display(artwork.previewLocation)}</Descriptions.Item>
        </Descriptions>
      </section>

      <section className="mobile-detail-section">
        <Divider orientation="left">系统信息</Divider>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="来源任务">{display(artwork.taskName)}</Descriptions.Item>
          <Descriptions.Item label="抓取时间">{artwork.createdAt?.replace('T', ' ').slice(0, 19) || '—'}</Descriptions.Item>
          <Descriptions.Item label="原图状态">{imageStatusText(artwork.originalImageStatus)}</Descriptions.Item>
          <Descriptions.Item label="高清图状态">{artwork.hdImageAvailable ? '已同步' : imageStatusText(artwork.hdImageStatus)}</Descriptions.Item>
        </Descriptions>
      </section>
    </MobileDataLayout>
  )
}
