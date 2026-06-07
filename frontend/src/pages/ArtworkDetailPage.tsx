import { useEffect, useState } from 'react'
import {
  Badge,
  Breadcrumb,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Image,
  message,
  Row,
  Skeleton,
  Space,
  Tooltip,
  Typography,
} from 'antd'
import { ArrowLeftOutlined, DollarOutlined, DownloadOutlined, LinkOutlined, PictureOutlined } from '@ant-design/icons'
import { Link, useNavigate, useParams } from 'react-router-dom'
import * as api from '../api'
import type { Artwork } from '../types'
import { useAuth } from '../auth/AuthContext'
import { permissions } from '../auth/permissions'

export default function ArtworkDetailPage() {
  const { hasPermission } = useAuth()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [artwork, setArtwork] = useState<Artwork | null>(null)
  const [loading, setLoading] = useState(true)
  const [redownloading, setRedownloading] = useState(false)
  const [supplementingPrice, setSupplementingPrice] = useState(false)

  useEffect(() => {
    if (!id) return
    api.getArtwork(Number(id))
      .then(setArtwork)
      .catch((e) => { message.error(e.message); navigate('/artworks') })
      .finally(() => setLoading(false))
  }, [id])

  if (loading) {
    return (
      <Card>
        <Skeleton active avatar={{ size: 200 }} paragraph={{ rows: 8 }} />
      </Card>
    )
  }

  if (!artwork) return null

  const canViewOriginal = Boolean(artwork.originalImageAvailable || artwork.originalImageSourceUrl || artwork.sourceUrl || artwork.imageUrl)
  const originalImageViewerUrl = `/artworks/${artwork.id}/images/original`
  const hdImageViewerUrl = `/artworks/${artwork.id}/images/hd`
  const hdImageTooltip = artwork.hdImageAvailable
    ? '从本地打开已下载的超清无损图'
    : artwork.hdImageStatus === 'FAILED'
      ? `超清无损图下载失败：${artwork.hdImageLastError || '请在任务管理中重新运行补充超清无损图任务'}`
      : '超清无损图尚未下载，请先在任务管理中创建并运行补充超清无损图任务'

  const handleRedownloadOriginal = async () => {
    if (!artwork) return
    setRedownloading(true)
    try {
      const updated = await api.redownloadOriginalImage(artwork.id)
      setArtwork(updated)
      message.success('原图已重新下载')
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setRedownloading(false)
    }
  }

  const handleSupplementTransactionPrice = async () => {
    if (!artwork) return
    setSupplementingPrice(true)
    try {
      const updated = await api.supplementTransactionPrice(artwork.id)
      setArtwork(updated)
      if (updated.transactionPrice) {
        message.success(`成交价已补充：${updated.transactionPrice}`)
      } else {
        message.info(`未拿到成交价：${updated.transactionPriceNote || '待补充'}`)
      }
    } catch (e: any) {
      message.error(e.message)
    } finally {
      setSupplementingPrice(false)
    }
  }

  const transactionPriceDisplay = artwork.transactionPrice
    ? artwork.transactionPrice
    : <span style={{ color: '#999' }}>{artwork.transactionPriceNote || '待补充'}</span>

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <Link to="/artworks">艺术品数据</Link> },
          { title: artwork.title },
        ]}
      />

      <Card
        title={
          <Space>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)} />
            <Typography.Title level={4} style={{ margin: 0 }}>{artwork.title}</Typography.Title>
          </Space>
        }
        extra={
          <Space>
            {hasPermission(permissions.artworkTransactionPriceSupplement) && (
              <Button
                icon={<DollarOutlined />}
                loading={supplementingPrice}
                onClick={handleSupplementTransactionPrice}
              >
                补充成交价
              </Button>
            )}
            {canViewOriginal && hasPermission(permissions.artworkImageView) && (
              <Button icon={<PictureOutlined />} href={originalImageViewerUrl} target="_blank" rel="noreferrer">
                查看已保存原图
              </Button>
            )}
            {hasPermission(permissions.artworkImageView) && (
              <Tooltip title={hdImageTooltip}>
              <span>
                <Button
                  icon={<PictureOutlined />}
                  href={artwork.hdImageAvailable ? hdImageViewerUrl : undefined}
                  target="_blank"
                  rel="noreferrer"
                  disabled={!artwork.hdImageAvailable}
                >
                  查看超清无损图
                </Button>
              </span>
              </Tooltip>
            )}
            {hasPermission(permissions.artworkImageRedownload) && (
              <Button icon={<DownloadOutlined />} loading={redownloading} onClick={handleRedownloadOriginal}>
                重新下载原图
              </Button>
            )}
            {artwork.sourceUrl && (
              <Button icon={<LinkOutlined />} href={artwork.sourceUrl} target="_blank">
                查看原始数据
              </Button>
            )}
          </Space>
        }
      >
        <Row gutter={[24, 24]}>
          {/* 图片 */}
          <Col xs={24} md={8}>
            {artwork.imageUrl ? (
              <Image
                src={artwork.imageUrl}
                style={{ width: '100%', maxHeight: 400, objectFit: 'contain', borderRadius: 8 }}
                fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
              />
            ) : (
              <div style={{
                width: '100%', height: 300, background: '#f0f0f0', borderRadius: 8,
                display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#aaa',
              }}>
                <Typography.Text type="secondary">暂无图片</Typography.Text>
              </div>
            )}
          </Col>

          {/* 详情 */}
          <Col xs={24} md={16}>
            {/* 拍品基本信息 */}
            <Descriptions bordered column={1} size="middle">
              <Descriptions.Item label="拍品名称">
                <Typography.Text strong>{artwork.title}</Typography.Text>
              </Descriptions.Item>
              {artwork.lotNumber && (
                <Descriptions.Item label="编号">
                  {artwork.lotNumber}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="作者">
                {artwork.artist || <span style={{ color: '#aaa' }}>未知</span>}
              </Descriptions.Item>
              <Descriptions.Item label="材质">
                {artwork.medium || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="形制">
                {artwork.format || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="尺寸">
                {artwork.dimensions || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="估价">
                {artwork.valuation || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="成交价">
                {transactionPriceDisplay}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ marginTop: 16, marginBottom: 8 }}>拍卖信息</Divider>

            <Descriptions bordered column={1} size="middle">
              <Descriptions.Item label="拍卖公司">
                {artwork.auctionHouse || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖会">
                {artwork.auctionName || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖专场">
                {artwork.auctionSession || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖日期">
                {artwork.auctionDate || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="拍卖地点">
                {artwork.auctionLocation || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="预展时间">
                {artwork.previewTime || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="预展地点">
                {artwork.previewLocation || '—'}
              </Descriptions.Item>
            </Descriptions>

            <Divider orientation="left" style={{ marginTop: 16, marginBottom: 8 }}>系统信息</Divider>

            <Descriptions bordered column={1} size="middle">
              <Descriptions.Item label="来源任务">
                <Badge color="blue" text={
                  <Link to={`/artworks?taskId=${artwork.taskId}`}>{artwork.taskName}</Link>
                } />
              </Descriptions.Item>
              <Descriptions.Item label="原图状态">
                {artwork.originalImageStatus === 'DOWNLOADED'
                  ? '已保存'
                  : artwork.originalImageStatus === 'FAILED'
                    ? '下载失败'
                    : '未保存'}
              </Descriptions.Item>
              <Descriptions.Item label="超清无损图状态">
                {artwork.hdImageAvailable ? (
                  <Space size={4}>
                    <Typography.Text>已保存本地</Typography.Text>
                    <Typography.Text type="secondary">可直接查看</Typography.Text>
                  </Space>
                ) : artwork.hdImageStatus === 'FAILED' ? (
                  artwork.hdImageLastError || '下载失败'
                ) : (
                  '未保存'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="抓取时间">
                {artwork.createdAt?.replace('T', ' ').slice(0, 19)}
              </Descriptions.Item>
            </Descriptions>

            <Card title="拍品描述" size="small" style={{ marginTop: 16 }}>
              {artwork.description?.trim() ? (
                <Typography.Paragraph
                  ellipsis={{ rows: 6, expandable: true, symbol: '展开' }}
                  style={{ margin: 0, whiteSpace: 'pre-wrap' }}
                >
                  {artwork.description}
                </Typography.Paragraph>
              ) : (
                <Typography.Text type="secondary">暂无拍品描述</Typography.Text>
              )}
            </Card>
          </Col>
        </Row>
      </Card>
    </div>
  )
}
